/*
USAGE INSTRUCTIONS FOR audio_request_collection

- This collection stores compressed audio data, synth information, preset files, and related metadata for inference requests.

- Required fields for each document:
    - inference_request_id: String (foreign key to MySQL INFERENCE_REQUEST.id)
    - synth: String (e.g., "vital")
    - audio_compressed: Binary (compressed audio data, must be included)
    - preset_file: Binary (preset file, must be included)
    - created_at: Unix timestamp (number, milliseconds since epoch)
    - updated_at: Unix timestamp (number, milliseconds since epoch)

- Optional fields:
    - preset_metadata: Object (JSON)
    - other_metadata: Object (JSON)

- Dates should be stored as Unix timestamps (e.g., Date.now() in JavaScript).

- Example: Insert a document
    db.audio_request_collection.insertOne({
        inference_request_id: "abc-123", //required
        synth: "vital", //required
        audio_compressed: BinData(0, "<base64-data>"), //required
        preset_file: BinData(0, "<base64-data>"), //required
        created_at: Date.now(), //required
        updated_at: Date.now(), //required
        preset_metadata: {},
        other_metadata: {}
    });

- Example: Find by _id (ObjectId)
    db.audio_request_collection.find({ _id: ObjectId("<id>") })

- Example: Find by inference_request_id
    db.audio_request_collection.find({ inference_request_id: "abc-123" })

- Always ensure required fields are present when inserting documents.
*/

// 'neural_synth' database in mongosh:
// > use neural_synth
// Then run: load('audio_request_collection_init.js')

// Create the collection if it doesn't exist
if (!db.getCollectionNames().includes('audio_request_collection')) {
    db.createCollection('audio_request_collection');
}

// Create an index on inference_request_id for fast lookups
// (foreign key to MySQL INFERENCE_REQUEST.id)
db.audio_request_collection.createIndex({ inference_request_id: 1 });

// Insert an example document
// Replace <base64-data> with actual base64-encoded binary data when inserting for real
// This is a test entry for db

db.audio_request_collection.insertOne({
    inference_request_id: "abc-123", // Foreign key to MySQL
    synth: "vital",                  // Synth name
    audio_gzipped: BinData(0, ""),   // Compressed audio data (placeholder)
    preset_file: BinData(0, ""),     // Preset file data
    preset_metadata: {},              // Preset metadata (optional)
    other_metadata: {},               // Any other relevant metadata (optional)
    created_at: Date.now(),           // Unix timestamp
    updated_at: Date.now()            // Unix timestamp
});

/*
USER SETUP INSTRUCTIONS

To create two users for the neural_synth database:

1. Read-Write User:
   db.createUser({
     user: "-",
     pwd: "-",
     roles: [ { role: "readWrite", db: "neural_synth" } ]
   })

2. Read-Only User:
   db.createUser({
     user: "-",
     pwd: "-",
     roles: [ { role: "read", db: "neural_synth" } ]
   })

Example connection URIs (for use in your application config):
CLI login - mongosh "mongodb://readonly:readonly@localhost:27017/neural_synth"
- Read-Write:
  mongodb://-:-@localhost:27017/neural_synth

- Read-Only:
  mongodb://-:-@localhost:27017/neural_synth

Replace 'localhost' and '27017' with your MongoDB host and port if different.
*/

/*
db.createUser({
  user: "-",
  pwd: "-",
  roles: [ { role: "readWrite", db: "neural_synth" } ]
})

db.createUser({
  user: "-",
  pwd: "-",
  roles: [ { role: "read", db: "neural_synth" } ]
}) */