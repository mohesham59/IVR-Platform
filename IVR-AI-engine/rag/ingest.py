import os
import re
import hashlib
import datetime
from typing import List, Dict, Any, Optional
import tiktoken
import chromadb
from chromadb.config import Settings
from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2

# Optional imports for document loading
try:
    from pypdf import PdfReader
except ImportError:
    PdfReader = None

try:
    import docx
except ImportError:
    docx = None

try:
    from bs4 import BeautifulSoup
except ImportError:
    BeautifulSoup = None

# Initialize Tiktoken Encoder
try:
    TOKENIZER = tiktoken.get_encoding("cl100k_base")
except Exception:
    TOKENIZER = None

def count_tokens(text: str) -> int:
    """Counts tokens using tiktoken cl100k_base or word approximation fallback."""
    if TOKENIZER:
        return len(TOKENIZER.encode(text))
    return int(len(text.split()) * 1.3)

class SmartChunker:
    """
    Splits text into 300-500 token chunks with ~15% overlap on semantic boundaries.
    """
    def __init__(self, target_chunk_size: int = 400, overlap_size: int = 60):
        self.target_chunk_size = target_chunk_size
        self.overlap_size = overlap_size

    def chunk_text(self, text: str, file_type: str) -> List[Dict[str, Any]]:
        if not text or not text.strip():
            return []

        # Choose initial semantic split delimiter based on file format
        if file_type in ['.vxml', '.xml', '.html']:
            blocks = re.split(r'(?=(?:<form|<menu|<catch|<block|<prompt|<grammar|<h[1-6]|<p))', text, flags=re.IGNORECASE)
        elif file_type in ['.md', '.markdown']:
            blocks = re.split(r'(?=\n#{1,4}\s+)', text)
        elif file_type == '.sql':
            blocks = re.split(r'(?=\n(?:CREATE TABLE|CREATE FUNCTION|INSERT INTO|ALTER TABLE)\b)', text, flags=re.IGNORECASE)
        else:
            blocks = text.split("\n\n")

        raw_chunks = []
        current_block = ""
        current_section = "General"

        for block in blocks:
            cleaned_block = block.strip()
            if not cleaned_block:
                continue

            # Detect section headers
            if file_type in ['.md', '.markdown'] and cleaned_block.startswith("#"):
                first_line = cleaned_block.split("\n")[0]
                current_section = first_line.strip("# ").strip()
            elif file_type in ['.vxml', '.xml', '.html']:
                tag_match = re.search(r'<(?:form|menu|h[1-6]|prompt)[^>]*id=["\']?([^"\'>\s]+)', cleaned_block, re.IGNORECASE)
                if tag_match:
                    current_section = tag_match.group(1)

            block_tokens = count_tokens(cleaned_block)
            current_tokens = count_tokens(current_block)

            if current_tokens + block_tokens <= self.target_chunk_size:
                current_block = (current_block + "\n\n" + cleaned_block).strip()
            else:
                if current_block:
                    raw_chunks.append({"text": current_block, "section": current_section})
                
                # If block itself exceeds target_chunk_size, split by sentences/paragraphs
                if block_tokens > self.target_chunk_size:
                    sentences = re.split(r'(?<=[.!?])\s+', cleaned_block)
                    sub_chunk = ""
                    for sentence in sentences:
                        if count_tokens(sub_chunk + " " + sentence) <= self.target_chunk_size:
                            sub_chunk = (sub_chunk + " " + sentence).strip()
                        else:
                            if sub_chunk:
                                raw_chunks.append({"text": sub_chunk, "section": current_section})
                            sub_chunk = sentence
                    if sub_chunk:
                        current_block = sub_chunk
                else:
                    current_block = cleaned_block

        if current_block.strip():
            raw_chunks.append({"text": current_block.strip(), "section": current_section})

        # Apply ~15% overlap across chunks
        final_chunks = []
        for idx, item in enumerate(raw_chunks):
            chunk_text = item["text"]
            section = item["section"]

            if idx > 0 and self.overlap_size > 0:
                prev_text = raw_chunks[idx - 1]["text"]
                prev_words = prev_text.split()
                overlap_words = prev_words[-self.overlap_size:]
                chunk_text = " ".join(overlap_words) + " " + chunk_text

            final_chunks.append({
                "text": chunk_text.strip(),
                "section": section,
                "token_count": count_tokens(chunk_text)
            })

        return final_chunks

class DocumentIngestor:
    """
    Loads, parses, chunks, deduplicates, and embeds project documents into ChromaDB.
    """
    def __init__(self, db_path: str = "./chroma_db", collection_name: str = "nexus_ivr_rag"):
        self.db_path = db_path
        self.collection_name = collection_name
        self.chunker = SmartChunker(target_chunk_size=400, overlap_size=50)

        # Initialize Embedding Model (ONNXMiniLM_L6_V2 or OpenAI)
        self.openai_key = os.getenv("OPENAI_API_KEY")
        if self.openai_key:
            print("[Ingestor] Using OpenAI Embeddings (text-embedding-3-small)...")
            self.embedding_fn = None
        else:
            print("[Ingestor] Using Lightweight ONNX Embeddings (all-MiniLM-L6-v2)...")
            self.embedding_fn = ONNXMiniLM_L6_V2()

        # Initialize Persistent ChromaDB Client
        self.client = chromadb.PersistentClient(path=self.db_path)
        self.collection = self.client.get_or_create_collection(
            name=self.collection_name,
            metadata={"hnsw:space": "cosine"}
        )

    def generate_embeddings(self, texts: List[str]) -> List[List[float]]:
        if self.openai_key:
            import requests
            headers = {"Authorization": f"Bearer {self.openai_key}", "Content-Type": "application/json"}
            resp = requests.post(
                "https://api.openai.com/v1/embeddings",
                headers=headers,
                json={"model": "text-embedding-3-small", "input": texts}
            )
            data = resp.json()
            return [item["embedding"] for item in data["data"]]
        else:
            if hasattr(self.embedding_fn, "__call__"):
                return self.embedding_fn(texts)
            return self.embedding_fn.encode(texts, show_progress_bar=False).tolist()

    def parse_file(self, file_path: str) -> Optional[Dict[str, Any]]:
        if not os.path.exists(file_path):
            return None

        ext = os.path.splitext(file_path)[1].lower()
        file_name = os.path.basename(file_path)
        rel_path = os.path.relpath(file_path, start=os.getcwd())
        
        with open(file_path, 'rb') as f:
            content_bytes = f.read()
        checksum = hashlib.sha256(content_bytes).hexdigest()

        text = ""
        pages_or_sections = {}

        try:
            if ext == '.pdf':
                if not PdfReader:
                    print(f"[Warn] pypdf not installed, skipping {file_path}")
                    return None
                reader = PdfReader(file_path)
                for p_idx, page in enumerate(reader.pages):
                    p_text = page.extract_text() or ""
                    pages_or_sections[f"Page {p_idx + 1}"] = p_text
                    text += f"\n--- Page {p_idx + 1} ---\n" + p_text
            elif ext == '.docx':
                if not docx:
                    print(f"[Warn] python-docx not installed, skipping {file_path}")
                    return None
                doc = docx.Document(file_path)
                text = "\n".join([p.text for p in doc.paragraphs if p.text.strip()])
            elif ext == '.html':
                if BeautifulSoup:
                    soup = BeautifulSoup(content_bytes, 'html.parser')
                    text = soup.get_text(separator="\n")
                else:
                    text = content_bytes.decode('utf-8', errors='ignore')
            else: # .md, .txt, .vxml, .csv, .sql, .json
                text = content_bytes.decode('utf-8', errors='ignore')

        except Exception as e:
            print(f"[Error] Failed to read {file_path}: {e}")
            return None

        if not text.strip():
            return None

        return {
            "source_name": file_name,
            "rel_path": rel_path,
            "file_type": ext,
            "checksum": checksum,
            "text": text,
            "date_added": datetime.datetime.now(datetime.timezone.utc).isoformat()
        }

    def ingest_file(self, file_path: str) -> int:
        parsed = self.parse_file(file_path)
        if not parsed:
            return 0

        checksum = parsed["checksum"]
        # Check if file is already ingested with exact checksum
        existing = self.collection.get(where={"checksum": checksum})
        if existing and existing["ids"]:
            print(f"[Ingestor] File {parsed['source_name']} unchanged (checksum match). Skipping.")
            return 0

        # Remove older version of file if updated
        old_version = self.collection.get(where={"source_name": parsed['source_name']})
        if old_version and old_version["ids"]:
            self.collection.delete(ids=old_version["ids"])

        chunks = self.chunker.chunk_text(parsed["text"], parsed["file_type"])
        if not chunks:
            return 0

        chunk_texts = [c["text"] for c in chunks]
        embeddings = self.generate_embeddings(chunk_texts)

        doc_id = hashlib.md5(parsed["rel_path"].encode('utf-8')).hexdigest()[:12]
        
        ids = []
        metadatas = []
        documents = []

        seen_chunk_hashes = set()

        for idx, chunk in enumerate(chunks):
            # Deduplication: Compute hash of chunk text
            chunk_hash = hashlib.md5(chunk["text"].encode('utf-8')).hexdigest()
            if chunk_hash in seen_chunk_hashes:
                continue
            seen_chunk_hashes.add(chunk_hash)

            chunk_id = f"doc_{doc_id}_chunk_{idx}"
            ids.append(chunk_id)
            documents.append(chunk["text"])
            metadatas.append({
                "unique_doc_id": f"doc_{doc_id}",
                "chunk_id": chunk_id,
                "source_name": parsed["source_name"],
                "rel_path": parsed["rel_path"],
                "file_type": parsed["file_type"],
                "date_added": parsed["date_added"],
                "section_or_page": chunk["section"],
                "checksum": checksum,
                "token_count": chunk["token_count"]
            })

        if ids:
            self.collection.add(
                ids=ids,
                documents=documents,
                embeddings=embeddings,
                metadatas=metadatas
            )
            print(f"[Ingestor] Ingested {len(ids)} chunks from {parsed['source_name']}")

        return len(ids)

if __name__ == "__main__":
    import sys
    ingestor = DocumentIngestor()
    if len(sys.argv) > 1:
        for path in sys.argv[1:]:
            if os.path.exists(path):
                added = ingestor.ingest_file(path)
                print(f"[Ingestor] Ingested '{path}' -> {added} chunk(s) added to ChromaDB.")
            else:
                print(f"[Ingestor Error] File not found: '{path}'")
    else:
        print("[Ingestor] Ingestor initialized. Pass file paths as arguments to ingest external files.")
        print("Usage: python ingest.py /path/to/document.pdf /path/to/data.csv")
