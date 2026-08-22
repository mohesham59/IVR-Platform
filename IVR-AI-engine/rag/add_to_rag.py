#!/usr/bin/env python3
import sys
import os
import requests
from ingest import DocumentIngestor

RAG_URL = os.getenv("RAG_SERVICE_URL", "http://localhost:8085")

def ingest_file(file_path: str):
    if not os.path.exists(file_path):
        print(f"❌ Error: File '{file_path}' does not exist.")
        sys.exit(1)
        
    file_path = os.path.abspath(file_path)
    print(f"📄 Adding external file to ChromaDB vector store: {file_path}")
    
    # Try via running microservice API first
    try:
        url = f"{RAG_URL.rstrip('/')}/ingest"
        response = requests.post(url, json={"file_path": file_path}, timeout=10)
        if response.status_code == 200:
            res = response.json()
            print(f"✅ Successfully ingested via REST API: {res.get('chunks_added', 0)} chunks added.")
            return
    except Exception:
        print("⚠️ REST API not reachable, falling back to direct ChromaDB ingestion...")

    # Direct local ingestion fallback
    db_path = os.path.join(os.path.dirname(__file__), "chroma_db")
    ingestor = DocumentIngestor(db_path=db_path)
    chunks_added = ingestor.ingest_file(file_path)
    print(f"✅ Ingested directly into ChromaDB: {chunks_added} chunks added.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python add_to_rag.py /path/to/external_file.pdf")
        sys.exit(1)
    
    for target in sys.argv[1:]:
        ingest_file(target)
