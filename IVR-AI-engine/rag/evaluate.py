import os
import json
from typing import List, Dict, Any
from retriever import RAGRetriever

# 20 Domain-Specific Evaluation Test Cases for NexusIVR Platform
EVALUATION_QUESTIONS = [
    {
        "id": 1,
        "question": "What port does the FastAGI server listen on?",
        "expected_keywords": ["4573", "FastAGI", "DefaultAgiServer"],
        "expected_source": "04_IVR_Engine_FastAGI_Specification.md"
    },
    {
        "id": 2,
        "question": "What servlet endpoint handles AI chat messages in the backend?",
        "expected_keywords": ["/api/v1/ai/chat", "AiChatServlet"],
        "expected_source": "03_IVR_AI_Engine_Backend_Specification.md"
    },
    {
        "id": 3,
        "question": "Where are custom audio voice recordings stored for Asterisk?",
        "expected_keywords": ["/var/lib/asterisk/sounds/ivr-custom", "sounds"],
        "expected_source": "README.md"
    },
    {
        "id": 4,
        "question": "How does the backend reload Asterisk dialplan without manual password prompts?",
        "expected_keywords": ["add_extension.sh", "sudoers", "nexus_ivr"],
        "expected_source": "README.md"
    },
    {
        "id": 5,
        "question": "What database driver and connection pool are used in IVR-AI-engine?",
        "expected_keywords": ["postgresql", "HikariCP"],
        "expected_source": "02_Database_Schema_and_Migrations.md"
    },
    {
        "id": 6,
        "question": "What port does Tomcat run on for the nexusivr-ai-engine?",
        "expected_keywords": ["8081", "Tomcat", "cargo"],
        "expected_source": "README.md"
    },
    {
        "id": 7,
        "question": "What payment provider integration is supported in IVR-payment-service?",
        "expected_keywords": ["Paymob", "8082"],
        "expected_source": "05_IVR_Payment_Service_Specification.md"
    },
    {
        "id": 8,
        "question": "What VXML XML parser is used by the FastAGI IVR engine?",
        "expected_keywords": ["JVoiceXML", "vxml"],
        "expected_source": "README.md"
    },
    {
        "id": 9,
        "question": "How does VxmlToModelConverter transform VoiceXML into React Flow nodes?",
        "expected_keywords": ["VxmlToModelConverter", "prompt", "nodes"],
        "expected_source": "03_IVR_AI_Engine_Backend_Specification.md"
    },
    {
        "id": 10,
        "question": "What is the role of add_extension.sh in the platform?",
        "expected_keywords": ["extensions.conf", "dialplan", "Asterisk"],
        "expected_source": "README.md"
    },
    {
        "id": 11,
        "question": "What table stores AI chat sessions in PostgreSQL?",
        "expected_keywords": ["ai_sessions", "tenant_id"],
        "expected_source": "02_Database_Schema_and_Migrations.md"
    },
    {
        "id": 12,
        "question": "What endpoint returns system health and database status?",
        "expected_keywords": ["/health", "HealthServlet"],
        "expected_source": "08_Complete_REST_API_Endpoint_Reference.md"
    },
    {
        "id": 13,
        "question": "What LLM providers are supported in ProviderManager?",
        "expected_keywords": ["Gemini", "Groq", "Ollama"],
        "expected_source": "03_IVR_AI_Engine_Backend_Specification.md"
    },
    {
        "id": 14,
        "question": "What is the token limit target for smart chunking in the RAG pipeline?",
        "expected_keywords": ["300", "500", "tokens"],
        "expected_source": "PROJECT_OVERVIEW.md"
    },
    {
        "id": 15,
        "question": "How does the visual flow builder publish IVR scenarios?",
        "expected_keywords": ["scenarios", ".vxml", "Publish"],
        "expected_source": "README.md"
    },
    {
        "id": 16,
        "question": "Where are Master.csv Asterisk call logs stored?",
        "expected_keywords": ["cdr-csv", "Master.csv"],
        "expected_source": "docker-compose.yml"
    },
    {
        "id": 17,
        "question": "What port does the IVR Payment service run on?",
        "expected_keywords": ["8082", "ivr-payment-service"],
        "expected_source": "docker-compose.yml"
    },
    {
        "id": 18,
        "question": "How are dead-end nodes validated before publishing an IVR flow?",
        "expected_keywords": ["validation", "FlowValidationResponse"],
        "expected_source": "03_IVR_AI_Engine_Backend_Specification.md"
    },
    {
        "id": 19,
        "question": "What channel types are supported in AiSession?",
        "expected_keywords": ["CHAT", "VOICE", "Channel"],
        "expected_source": "03_IVR_AI_Engine_Backend_Specification.md"
    },
    {
        "id": 20,
        "question": "What command starts the FastAGI server in IVR-engine?",
        "expected_keywords": ["DefaultAgiServer", "exec:java"],
        "expected_source": "README.md"
    }
]

def run_evaluation():
    db_path = os.path.join(os.path.dirname(__file__), "chroma_db")
    retriever = RAGRetriever(db_path=db_path)
    
    total = len(EVALUATION_QUESTIONS)
    passed_precision = 0
    passed_source = 0
    total_precision_score = 0.0

    print("=" * 70)
    print(" 🧪 RUNNING NEXUSIVR RAG RETRIEVAL & CITATION EVALUATION SUITE")
    print("=" * 70)

    for test in EVALUATION_QUESTIONS:
        qid = test["id"]
        q = test["question"]
        expected_kw = test["expected_keywords"]
        expected_src = test["expected_source"]

        res = retriever.search(query=q, top_k=5, min_score_threshold=0.25)
        chunks = res["chunks"]

        # 1. Evaluate Keyword Precision
        retrieved_text = " ".join([c["content"] for c in chunks]).lower()
        matched_kw = [kw for kw in expected_kw if kw.lower() in retrieved_text]
        kw_precision = len(matched_kw) / len(expected_kw) if expected_kw else 1.0
        total_precision_score += kw_precision

        if kw_precision >= 0.5:
            passed_precision += 1

        # 2. Evaluate Source Citation Accuracy
        retrieved_sources = [c["source_name"] for c in chunks]
        source_matched = any(expected_src.lower() in src.lower() for src in retrieved_sources)
        if source_matched:
            passed_source += 1

        status_str = "✅ PASS" if (kw_precision >= 0.5 and source_matched) else "⚠️ WARN"
        print(f"Test #{qid:02d}: {status_str} | Question: '{q}'")
        print(f"         Precision: {kw_precision*100:.1f}% | Source Match ({expected_src}): {'Yes' if source_matched else 'No'}")
        if chunks:
            top_src = chunks[0]["source_name"]
            top_score = chunks[0]["score"]
            print(f"         Top Chunk: [Source: {top_src}, Score: {top_score}]")
        print("-" * 70)

    mean_precision = (total_precision_score / total) * 100
    precision_pass_rate = (passed_precision / total) * 100
    source_pass_rate = (passed_source / total) * 100

    print("\n" + "=" * 70)
    print(" 📊 EVALUATION RESULTS SUMMARY")
    print("=" * 70)
    print(f" Total Evaluation Questions: {total}")
    print(f" Mean Keyword Precision@5  : {mean_precision:.2f}%")
    print(f" Precision Pass Rate (>=50%): {precision_pass_rate:.1f}% ({passed_precision}/{total})")
    print(f" Source Citation Match Rate : {source_pass_rate:.1f}% ({passed_source}/{total})")
    print("=" * 70 + "\n")

    return {
        "total_questions": total,
        "mean_precision": mean_precision,
        "precision_pass_rate": precision_pass_rate,
        "source_citation_match_rate": source_pass_rate
    }

if __name__ == "__main__":
    run_evaluation()
