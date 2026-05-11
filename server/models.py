from sqlalchemy import Column, Integer, String, ForeignKey, UniqueConstraint
from database import Base


class Habit(Base):
    __tablename__ = "habits"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False)
    icon = Column(String, default="🎯")
    created_at = Column(String, nullable=False)


class Completion(Base):
    __tablename__ = "completions"

    id = Column(Integer, primary_key=True, index=True)
    habit_id = Column(Integer, ForeignKey("habits.id", ondelete="CASCADE"), nullable=False)
    date = Column(String, nullable=False)

    __table_args__ = (
        UniqueConstraint("habit_id", "date", name="uq_habit_date"),
    )