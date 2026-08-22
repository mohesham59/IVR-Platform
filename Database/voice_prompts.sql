CREATE TABLE IF NOT EXISTS voice_prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    language VARCHAR(50) NOT NULL,
    duration VARCHAR(20),  -- e.g., "0:14"
    type VARCHAR(50) NOT NULL, -- e.g., 'Uploaded', 'AI Generated'
    created_by VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    
    -- Additional recommended fields:
    size_bytes BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
