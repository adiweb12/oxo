import os
from fastapi import FastAPI, Request, Form
from fastapi.templating import Jinja2Templates
from fastapi.responses import HTMLResponse
import psycopg2
from google import genai
from google.genai import types

app = FastAPI()
templates = Jinja2Templates(directory="templates")

# Configuration
API_KEY = os.environ.get("GEMINI_API_KEY")
DATABASE_URL = os.environ.get("DATABASE_URL")
client = genai.Client(api_key=API_KEY)

# Initialize Database
def init_db():
    conn = psycopg2.connect(DATABASE_URL)
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS chat_history (
            id SERIAL PRIMARY KEY,
            role TEXT,
            content TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()
    cur.close()
    conn.close()

init_db()

def get_fallback_response(user_input, history):
    # Models ordered by preference
    models = ["gemini-3-flash", "gemini-2.5-flash", "gemini-2.5-lite", "gemini-1.5-flash"]
    
    formatted_history = [{"role": r, "parts": [{"text": c}]} for r, c in history]
    
    for model_name in models:
        try:
            response = client.models.generate_content(
                model=model_name,
                contents=formatted_history + [{"role": "user", "parts": [{"text": user_input}]}],
                config=types.GenerateContentConfig(
                    system_instruction="You are a professional senior coder. Fix bugs, optimize, and document the code provided."
                )
            )
            return response.text, model_name
        except Exception as e:
            print(f"Skipping {model_name} due to error: {e}")
            continue
    return "Error: All models failed.", "None"

@app.get("/", response_class=HTMLResponse)
async def home(request: Request):
    return templates.TemplateResponse("index.html", {"request": request, "history": []})

@app.post("/process", response_class=HTMLResponse)
async def process_code(request: Request, user_code: str = Form(...)):
    conn = psycopg2.connect(DATABASE_URL)
    cur = conn.cursor()

    # 1. Fetch Permanent Memory
    cur.execute("SELECT role, content FROM chat_history ORDER BY created_at ASC")
    history = cur.fetchall()

    # 2. Get AI Response via Fallback
    ai_response, model_used = get_fallback_response(user_code, history)

    # 3. Save to Permanent Memory
    cur.execute("INSERT INTO chat_history (role, content) VALUES (%s, %s)", ("user", user_code))
    cur.execute("INSERT INTO chat_history (role, content) VALUES (%s, %s)", ("model", ai_response))
    conn.commit()
    
    # 4. Fetch updated history to show on UI
    cur.execute("SELECT role, content FROM chat_history ORDER BY created_at ASC")
    updated_history = cur.fetchall()
    
    cur.close()
    conn.close()

    return templates.TemplateResponse("index.html", {
        "request": request, 
        "history": updated_history,
        "model_used": model_used
    })
