-- How a job got its tags: 'llm' (model answer passed validation) or 'fallback' (rule-based after the model failed).
-- Lets us measure accuracy on LLM tags only and see how often the fallback kicks in.
ALTER TABLE jobs ADD COLUMN tag_status TEXT;
