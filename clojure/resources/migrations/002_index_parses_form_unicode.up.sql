CREATE INDEX IF NOT EXISTS idx_parses_form_unicode_coalesced
  ON parses (COALESCE(form_unicode, form))
