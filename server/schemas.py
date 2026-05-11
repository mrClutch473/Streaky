from pydantic import BaseModel
from typing import Optional


# --- Habit ---

class HabitCreate(BaseModel):
    name: str
    icon: Optional[str] = "🎯"


class HabitResponse(BaseModel):
    id: int
    name: str
    icon: str
    streak: int
    completed_today: bool
    created_at: str

    class Config:
        from_attributes = True


# --- Complete ---

class CompleteResponse(BaseModel):
    id: int
    completed_today: bool
    streak: int


# --- Stats ---

class HabitStats(BaseModel):
    id: int
    name: str
    icon: str
    streak: int
    best_streak: int
    total_completions: int
    last_30_days: list[str]
    created_at: str