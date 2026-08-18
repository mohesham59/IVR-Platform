FROM python:3.12-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy RAG engine source code
COPY . .

EXPOSE 8085

# On startup, run repository sync to populate vector database and start server
CMD ["sh", "-c", "python rag_sync.py && python server.py"]
