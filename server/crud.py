from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError
from datetime import date, timedelta
from models import Habit, Completion
from schemas import HabitCreate


def calculate_streak(dates: list[str]) -> int:
    if not dates:
        return 0

    sorted_dates = sorted(set(dates), reverse=True)
    today = date.today()
    yesterday = today - timedelta(days=1)

    # Стрик считается от сегодня если уже отметили,
    # иначе от вчера — чтобы не обнулять стрик до конца дня
    latest = date.fromisoformat(sorted_dates[0])
    if latest == today:
        expected = today
    elif latest == yesterday:
        expected = yesterday
    else:
        return 0  # последнее выполнение раньше вчера — стрик сломан

    streak = 0
    for d in sorted_dates:
        completion_date = date.fromisoformat(d)
        if completion_date == expected:
            streak += 1
            expected -= timedelta(days=1)
        else:
            break

    return streak


def calculate_best_streak(dates: list[str]) -> int:
    if not dates:
        return 0

    sorted_dates = sorted(set(dates))
    best = 1
    current = 1

    for i in range(1, len(sorted_dates)):
        prev = date.fromisoformat(sorted_dates[i - 1])
        curr = date.fromisoformat(sorted_dates[i])
        if (curr - prev).days == 1:
            current += 1
            best = max(best, current)
        else:
            current = 1

    return best


# --- Habits CRUD ---

def get_all_habits(db: Session) -> list[dict]:
    habits = db.query(Habit).all()
    today = str(date.today())
    result = []

    for habit in habits:
        completions = db.query(Completion).filter(Completion.habit_id == habit.id).all()
        dates = [c.date for c in completions]

        print(f"[DEBUG] '{habit.name}' | dates in DB: {dates} | today: {today}")

        streak = calculate_streak(dates)
        completed_today = today in dates

        result.append({
            "id": habit.id,
            "name": habit.name,
            "icon": habit.icon,
            "streak": streak,
            "completed_today": completed_today,
            "created_at": habit.created_at,
        })

    return result


def create_habit(db: Session, data: HabitCreate) -> dict:
    habit = Habit(name=data.name, icon=data.icon, created_at=str(date.today()))
    db.add(habit)
    db.commit()
    db.refresh(habit)

    return {
        "id": habit.id,
        "name": habit.name,
        "icon": habit.icon,
        "streak": 0,
        "completed_today": False,
        "created_at": habit.created_at,
    }


def toggle_completion(db: Session, habit_id: int) -> dict | None:
    habit = db.query(Habit).filter(Habit.id == habit_id).first()
    if not habit:
        return None

    today = str(date.today())
    existing = db.query(Completion).filter(
        Completion.habit_id == habit_id,
        Completion.date == today
    ).first()

    if existing:
        db.delete(existing)
        db.commit()
    else:
        completion = Completion(habit_id=habit_id, date=today)
        db.add(completion)
        db.commit()

    completions = db.query(Completion).filter(Completion.habit_id == habit_id).all()
    dates = [c.date for c in completions]
    streak = calculate_streak(dates)
    completed_today = today in dates

    return {
        "id": habit_id,
        "completed_today": completed_today,
        "streak": streak,
    }


def get_habit_stats(db: Session, habit_id: int) -> dict | None:
    habit = db.query(Habit).filter(Habit.id == habit_id).first()
    if not habit:
        return None

    completions = db.query(Completion).filter(Completion.habit_id == habit_id).all()
    dates = [c.date for c in completions]

    today = date.today()
    last_30 = [(today - timedelta(days=i)).isoformat() for i in range(29, -1, -1)]
    last_30_completed = [d for d in last_30 if d in dates]

    return {
        "id": habit.id,
        "name": habit.name,
        "icon": habit.icon,
        "streak": calculate_streak(dates),
        "best_streak": calculate_best_streak(dates),
        "total_completions": len(dates),
        "last_30_days": last_30_completed,
        "created_at": habit.created_at,
    }


def delete_habit(db: Session, habit_id: int) -> bool:
    habit = db.query(Habit).filter(Habit.id == habit_id).first()
    if not habit:
        return False

    db.query(Completion).filter(Completion.habit_id == habit_id).delete()
    db.delete(habit)
    db.commit()
    return True