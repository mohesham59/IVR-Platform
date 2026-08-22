import os
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from ingest import DocumentIngestor
from retriever import RAGRetriever
from rag_sync import run_repository_sync

app = FastAPI(title="NexusIVR RAG Microservice", version="1.0.0")

# Lazy-loaded singletons
retriever_instance: Optional[RAGRetriever] = None
ingestor_instance: Optional[DocumentIngestor] = None

def get_retriever() -> RAGRetriever:
    global retriever_instance
    if retriever_instance is None:
        db_path = os.path.join(os.path.dirname(__file__), "chroma_db")
        retriever_instance = RAGRetriever(db_path=db_path)
    return retriever_instance

def get_ingestor() -> DocumentIngestor:
    global ingestor_instance
    if ingestor_instance is None:
        db_path = os.path.join(os.path.dirname(__file__), "chroma_db")
        ingestor_instance = DocumentIngestor(db_path=db_path)
    return ingestor_instance

class QueryRequest(BaseModel):
    query: str
    top_k: Optional[int] = 5
    min_score: Optional[float] = 0.35

class IngestRequest(BaseModel):
    file_path: str

@app.get("/health")
def health_check():
    retriever = get_retriever()
    total_docs = retriever.collection.count()
    return {
        "status": "UP",
        "service": "NexusIVR RAG Engine",
        "total_indexed_chunks": total_docs
    }

@app.post("/query")
def query_rag(req: QueryRequest):
    if not req.query or not req.query.strip():
        raise HTTPException(status_code=400, detail="Query cannot be empty")
    
    retriever = get_retriever()
    result = retriever.search(
        query=req.query,
        top_k=req.top_k or 5,
        min_score_threshold=req.min_score or 0.35
    )
    return result

class IngestTextRequest(BaseModel):
    source_name: str
    text: str
    file_type: Optional[str] = ".txt"
    section: Optional[str] = "External Input"

@app.post("/ingest")
def ingest_document(req: IngestRequest):
    if not os.path.exists(req.file_path):
        raise HTTPException(status_code=404, detail=f"File not found: {req.file_path}")
    
    ingestor = get_ingestor()
    added = ingestor.ingest_file(req.file_path)
    return {"status": "SUCCESS", "chunks_added": added, "file": req.file_path}

@app.post("/ingest_text")
def ingest_text_content(req: IngestTextRequest):
    if not req.text or not req.text.strip():
        raise HTTPException(status_code=400, detail="Text content cannot be empty")
    
    ingestor = get_ingestor()
    uploads_dir = os.path.join(os.path.dirname(__file__), "uploads")
    os.makedirs(uploads_dir, exist_ok=True)
    
    safe_name = os.path.basename(req.source_name)
    if not safe_name.endswith(req.file_type or ".txt"):
        safe_name += (req.file_type or ".txt")
        
    temp_file = os.path.join(uploads_dir, safe_name)
    with open(temp_file, "w", encoding="utf-8") as f:
        f.write(req.text)
    
    added = ingestor.ingest_file(temp_file)
    return {"status": "SUCCESS", "chunks_added": added, "source_name": safe_name, "saved_path": temp_file}

@app.post("/sync")
def sync_repository():
    total = run_repository_sync()
    return {"status": "SUCCESS", "total_indexed_chunks": total}

if __name__ == "__main__":
    uvicorn.run("server:app", host="0.0.0.0", port=8085, reload=False)
