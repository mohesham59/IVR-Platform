import os
import math
from typing import List, Dict, Any, Tuple
import numpy as np
from rank_bm25 import BM25Okapi
import chromadb
from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2

class RAGRetriever:
    """
    Hybrid (Vector + BM25) Retriever with MMR Re-ranking and Similarity Threshold Fallback.
    """
    def __init__(self, db_path: str = "./chroma_db", collection_name: str = "nexus_ivr_rag"):
        self.db_path = db_path
        self.collection_name = collection_name
        self.openai_key = os.getenv("OPENAI_API_KEY")

        if not self.openai_key:
            self.embedding_fn = ONNXMiniLM_L6_V2()
        else:
            self.embedding_fn = None

        self.client = chromadb.PersistentClient(path=self.db_path)
        self.collection = self.client.get_or_create_collection(
            name=self.collection_name,
            metadata={"hnsw:space": "cosine"}
        )

    def embed_query(self, query: str) -> List[float]:
        if self.openai_key:
            import requests
            headers = {"Authorization": f"Bearer {self.openai_key}", "Content-Type": "application/json"}
            resp = requests.post(
                "https://api.openai.com/v1/embeddings",
                headers=headers,
                json={"model": "text-embedding-3-small", "input": [query]}
            )
            data = resp.json()
            return data["data"][0]["embedding"]
        else:
            if hasattr(self.embedding_fn, "__call__"):
                return self.embedding_fn([query])[0]
            return self.embedding_fn.encode(query, show_progress_bar=False).tolist()

    def _reciprocal_rank_fusion(self, vector_results: List[Dict], bm25_results: List[Dict], top_k: int) -> List[Dict]:
        """Combines dense vector search and sparse BM25 search via Reciprocal Rank Fusion (RRF)."""
        rrf_scores: Dict[str, float] = {}
        doc_map: Dict[str, Dict] = {}

        # Process Vector Results
        for rank, item in enumerate(vector_results):
            cid = item["chunk_id"]
            doc_map[cid] = item
            rrf_scores[cid] = rrf_scores.get(cid, 0.0) + (1.0 / (60 + rank + 1))

        # Process BM25 Results
        for rank, item in enumerate(bm25_results):
            cid = item["chunk_id"]
            if cid not in doc_map:
                doc_map[cid] = item
            rrf_scores[cid] = rrf_scores.get(cid, 0.0) + (1.0 / (60 + rank + 1))

        # Sort by combined RRF score
        sorted_ids = sorted(rrf_scores.keys(), key=lambda x: rrf_scores[x], reverse=True)
        fusion_list = []
        for cid in sorted_ids[:top_k * 2]:
            item = doc_map[cid]
            item["rrf_score"] = rrf_scores[cid]
            fusion_list.append(item)

        return fusion_list

    def _maximal_marginal_relevance(
        self,
        query_vec: List[float],
        candidates: List[Dict],
        top_k: int,
        lambda_param: float = 0.7
    ) -> List[Dict]:
        """Applies MMR to balance query relevance and diversity among selected retrieved chunks."""
        if not candidates or len(candidates) <= top_k:
            return candidates[:top_k]

        if not self.embedding_fn:
            # Fallback to RRF ordering if using external API embeddings
            return candidates[:top_k]

        if hasattr(self.embedding_fn, "__call__"):
            cand_embeddings = np.array(self.embedding_fn([c["content"] for c in candidates]))
        else:
            cand_embeddings = np.array(self.embedding_fn.encode([c["content"] for c in candidates], show_progress_bar=False))
        query_emb = np.array(query_vec)

        selected_indices = []
        unselected_indices = list(range(len(candidates)))

        while len(selected_indices) < top_k and unselected_indices:
            best_idx = -1
            best_score = -float("inf")

            for idx in unselected_indices:
                doc_emb = cand_embeddings[idx]
                sim_to_query = float(np.dot(query_emb, doc_emb) / (np.linalg.norm(query_emb) * np.linalg.norm(doc_emb) + 1e-9))

                if not selected_indices:
                    mmr_score = sim_to_query
                else:
                    max_sim_to_selected = max([
                        float(np.dot(doc_emb, cand_embeddings[s_idx]) / (np.linalg.norm(doc_emb) * np.linalg.norm(cand_embeddings[s_idx]) + 1e-9))
                        for s_idx in selected_indices
                    ])
                    mmr_score = lambda_param * sim_to_query - (1 - lambda_param) * max_sim_to_selected

                if mmr_score > best_score:
                    best_score = mmr_score
                    best_idx = idx

            if best_idx != -1:
                selected_indices.append(best_idx)
                unselected_indices.remove(best_idx)

        return [candidates[i] for i in selected_indices]

    def search(self, query: str, top_k: int = 5, min_score_threshold: float = 0.35) -> Dict[str, Any]:
        """
        Executes Top-K Hybrid Search with MMR and score threshold evaluation.
        """
        if not query or not query.strip():
            return {"chunks": [], "fallback_required": True, "reason": "Empty query"}

        count = self.collection.count()
        if count == 0:
            return {"chunks": [], "fallback_required": True, "reason": "Vector DB empty"}

        # 1. Dense Vector Query
        query_emb = self.embed_query(query)
        v_results = self.collection.query(
            query_embeddings=[query_emb],
            n_results=min(top_k * 3, count),
            include=["documents", "metadatas", "distances"]
        )

        vector_candidates = []
        if v_results and v_results["documents"] and v_results["documents"][0]:
            docs = v_results["documents"][0]
            metas = v_results["metadatas"][0]
            dists = v_results["distances"][0]

            for d, m, dist in zip(docs, metas, dists):
                # ChromaDB distance is cosine distance (1 - similarity)
                similarity = max(0.0, 1.0 - dist)
                vector_candidates.append({
                    "chunk_id": m.get("chunk_id", ""),
                    "unique_doc_id": m.get("unique_doc_id", ""),
                    "source_name": m.get("source_name", ""),
                    "rel_path": m.get("rel_path", ""),
                    "file_type": m.get("file_type", ""),
                    "section_or_page": m.get("section_or_page", ""),
                    "content": d,
                    "score": round(similarity, 4)
                })

        # 2. Sparse BM25 Search
        all_data = self.collection.get(include=["documents", "metadatas"])
        bm25_candidates = []
        if all_data and all_data["documents"]:
            corpus = [doc.lower().split() for doc in all_data["documents"]]
            bm25 = BM25Okapi(corpus)
            tokenized_query = query.lower().split()
            scores = bm25.get_scores(tokenized_query)
            top_indices = np.argsort(scores)[::-1][:min(top_k * 3, len(corpus))]

            for idx in top_indices:
                if scores[idx] > 0.0:
                    m = all_data["metadatas"][idx]
                    bm25_candidates.append({
                        "chunk_id": m.get("chunk_id", ""),
                        "unique_doc_id": m.get("unique_doc_id", ""),
                        "source_name": m.get("source_name", ""),
                        "rel_path": m.get("rel_path", ""),
                        "file_type": m.get("file_type", ""),
                        "section_or_page": m.get("section_or_page", ""),
                        "content": all_data["documents"][idx],
                        "score": round(float(scores[idx]), 4)
                    })

        # 3. Hybrid RRF Combination
        hybrid_candidates = self._reciprocal_rank_fusion(vector_candidates, bm25_candidates, top_k)

        # 4. Re-rank with MMR
        reranked_chunks = self._maximal_marginal_relevance(query_emb, hybrid_candidates, top_k)

        # 5. Check Similarity Threshold
        max_score = max([c["score"] for c in vector_candidates], default=0.0)
        fallback_required = max_score < min_score_threshold

        return {
            "chunks": reranked_chunks if not fallback_required else [],
            "fallback_required": fallback_required,
            "max_score": max_score,
            "threshold": min_score_threshold
        }

if __name__ == "__main__":
    import sys
    import json
    retriever = RAGRetriever()
    if len(sys.argv) > 1:
        query = " ".join(sys.argv[1:])
        print(f"[Retriever] Searching for query: '{query}'")
        res = retriever.search(query)
        print(json.dumps(res, indent=2))
    else:
        print("[Retriever] RAGRetriever initialized.")
