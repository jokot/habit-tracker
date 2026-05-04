-- Allow custom habits (templateId = null) to land in cloud.
ALTER TABLE habits ALTER COLUMN template_id DROP NOT NULL;
