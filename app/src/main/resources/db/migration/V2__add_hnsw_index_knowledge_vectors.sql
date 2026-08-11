-- Replace IVFFlat index with HNSW for better query performance
-- HNSW provides faster approximate nearest neighbor search
-- m=16: number of connections per layer, ef_construction=100: build-time quality

-- Drop old index if exists (name may vary, using IF EXISTS for safety)
DROP INDEX IF EXISTS idx_knowledge_vectors_embedding;

-- Create HNSW index with cosine distance
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding
  ON knowledge_base_vectors USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 100);
