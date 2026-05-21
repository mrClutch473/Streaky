from fastapi import FastAPI, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from database import engine, get_db, Base
import crud
from schemas import HabitCreate, HabitResponse, CompleteResponse, HabitStats, UserCreate, UserLogin, UserResponse
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


def get_current_user(user_id: int = Query(..., description="ID пользователя"), db: Session = Depends(get_db)):
    user = crud.get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="Пользователь не найден")
    return user


@app.post("/register", response_model=UserResponse, status_code=201)
def register(data: UserCreate, db: Session = Depends(get_db)):
    try:
        user = crud.register_user(db, data.email, data.password)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return user


@app.post("/login", response_model=UserResponse)
def login(data: UserLogin, db: Session = Depends(get_db)):
    user = crud.authenticate_user(db, data.email, data.password)
    if not user:
        raise HTTPException(status_code=401, detail="Неверный email или пароль")
    return user


@app.get("/habits", response_model=list[HabitResponse])
def get_habits(db: Session = Depends(get_db), user=Depends(get_current_user)):
    return crud.get_all_habits(db, user_id=user.id)


@app.post("/habits", response_model=HabitResponse, status_code=201)
def create_habit(data: HabitCreate, db: Session = Depends(get_db), user=Depends(get_current_user)):
    return crud.create_habit(db, data, user_id=user.id)


@app.post("/habits/{habit_id}/complete", response_model=CompleteResponse)
def toggle_complete(habit_id: int, db: Session = Depends(get_db), user=Depends(get_current_user)):
    result = crud.toggle_completion(db, habit_id, user_id=user.id)
    if not result:
        raise HTTPException(status_code=404, detail="Habit not found")
    return result


@app.get("/habits/{habit_id}/stats", response_model=HabitStats)
def get_stats(habit_id: int, db: Session = Depends(get_db), user=Depends(get_current_user)):
    result = crud.get_habit_stats(db, habit_id, user_id=user.id)
    if not result:
        raise HTTPException(status_code=404, detail="Habit not found")
    return result


@app.delete("/habits/{habit_id}", status_code=204)
def delete_habit(habit_id: int, db: Session = Depends(get_db), user=Depends(get_current_user)):
    deleted = crud.delete_habit(db, habit_id, user_id=user.id)
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