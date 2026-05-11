from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy.orm import Session
from database import engine, get_db, Base
import crud
from schemas import HabitCreate, HabitResponse, CompleteResponse, HabitStats
from fastapi.middleware.cors import CORSMiddleware
from datetime import date

Base.metadata.create_all(bind=engine)

app = FastAPI(title="Streaky API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/habits", response_model=list[HabitResponse])
def get_habits(db: Session = Depends(get_db)):
    return crud.get_all_habits(db)


@app.post("/habits", response_model=HabitResponse, status_code=201)
def create_habit(data: HabitCreate, db: Session = Depends(get_db)):
    return crud.create_habit(db, data)


@app.post("/habits/{habit_id}/complete", response_model=CompleteResponse)
def toggle_complete(habit_id: int, db: Session = Depends(get_db)):
    result = crud.toggle_completion(db, habit_id)
    if not result:
        raise HTTPException(status_code=404, detail="Habit not found")
    return result


@app.get("/habits/{habit_id}/stats", response_model=HabitStats)
def get_stats(habit_id: int, db: Session = Depends(get_db)):
    result = crud.get_habit_stats(db, habit_id)
    if not result:
        raise HTTPException(status_code=404, detail="Habit not found")
    return result


@app.delete("/habits/{habit_id}", status_code=204)
def delete_habit(habit_id: int, db: Session = Depends(get_db)):
    deleted = crud.delete_habit(db, habit_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Habit not found")

@app.get("/today")
def get_today():
    today = date.today()
    # Русские названия дней и месяцев
    days = ["Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье"]
    months = ["января","февраля","марта","апреля","мая","июня",
              "июля","августа","сентября","октября","ноября","декабря"]

    day_name  = days[today.weekday()]
    month_name = months[today.month - 1]

    return {
        "date_string": f"{day_name}, {today.day} {month_name}"
    }