/*
 * Copyright 2024 Open Pace Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Add JSONB column for custom activity objects (Run, Ride, Swim, etc.)
-- Dual storage: object_content (TEXT) for Notes, object_json (JSONB) for custom types
ALTER TABLE activities ADD COLUMN object_json JSONB;

-- GIN index for efficient JSONB property queries
-- Enables: SELECT * FROM activities WHERE object_json->>'distance' > '5000'
CREATE INDEX idx_activities_object_json ON activities USING GIN (object_json);
