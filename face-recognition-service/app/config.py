import os

# Cấu hình kết nối MongoDB riêng cho Face Recognition Service (face_db)
MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
DB_NAME = os.getenv("MONGODB_DATABASE", "face_db")

# Cổng chạy service theo quy định trong GEMINI.md
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8090"))