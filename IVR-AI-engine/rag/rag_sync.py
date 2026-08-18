import os
import glob
from ingest import DocumentIngestor

def run_repository_sync():
    """
    Scans project directories and ingests/syncs documents into ChromaDB vector store.
    """
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    print(f"[RAG Sync] Scanning repository root: {repo_root}")

    target_paths = [
        os.path.join(repo_root, "Documentation", "*.*"),
        os.path.join(repo_root, "IVR-engine", "scenarios", "*.*"),
        os.path.join(repo_root, "Database", "**", "*.*"),
        os.path.join(os.path.dirname(__file__), "chroma_db", "*.*"),
        os.path.join(os.path.dirname(__file__), "uploads", "*.*"),
        os.path.join(repo_root, "*.vxml"),
        os.path.join(repo_root, "*.md")
    ]

    files_to_sync = []
    for pattern in target_paths:
        files = glob.glob(pattern, recursive=True)
        for f in files:
            if os.path.isfile(f):
                ext = os.path.splitext(f)[1].lower()
                if ext in ['.md', '.txt', '.pdf', '.docx', '.vxml', '.html', '.csv', '.sql', '.json']:
                    files_to_sync.append(f)

    # Remove duplicates
    files_to_sync = list(set(files_to_sync))
    print(f"[RAG Sync] Found {len(files_to_sync)} candidate documents for sync.")

    ingestor = DocumentIngestor(db_path=os.path.join(os.path.dirname(__file__), "chroma_db"))
    total_chunks_added = 0

    for file_path in sorted(files_to_sync):
        rel_path = os.path.relpath(file_path, repo_root)
        try:
            chunks_added = ingestor.ingest_file(file_path)
            total_chunks_added += chunks_added
        except Exception as e:
            print(f"[RAG Sync Error] Failed to ingest {rel_path}: {e}")

    total_in_db = ingestor.collection.count()
    print(f"[RAG Sync Complete] Total new chunks added: {total_chunks_added}. Total chunks in DB: {total_in_db}")
    return total_in_db

if __name__ == "__main__":
    run_repository_sync()
