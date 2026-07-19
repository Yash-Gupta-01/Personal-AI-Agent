PRD (v1)
Project Name
Aether
Your second brain.
Objective
Build a fully local AI-powered personal assistant that continuously builds an understanding of the user's life and proactively assists with planning, reflection, memory, and execution.
Core Philosophy
The assistant should improve because its model of the user improves, not because the LLM is retrained.
The LLM is stateless.
The memory system is stateful.
Functional Requirements
Capture

Voice notes
Text notes
Quick capture widget
Clipboard capture (future)
Memory
Store:

goals
habits
tasks
projects
relationships
preferences
reflections
journal entries
Support:

semantic search
updates
merging
confidence scores
version history
Planning
Automatically:

create tasks
prioritize tasks
schedule work
detect procrastination
recommend changes
Journaling
Support:

free-form journal
guided reflection
AI summaries
mood trends
weekly reports
Habit Engine
Track:

streaks
consistency
completion
behavioral patterns
Generate insights rather than just charts.
Assistant
Should:

answer questions
search memory
create reminders
create alarms
update memories
retrieve context
invoke tools
ask clarifying questions when confidence is low
MCP Tool Layer
Expose:

Memory
Tasks
Calendar
Notifications
Alarms
Files
Search
Weather
Traffic
Maps
Device actions
Knowledge Graph
Represent relationships between:

people
projects
habits
goals
events
to enable richer reasoning than vector search alone.
Future Features (Out of Scope for v1)

Desktop sync
Wear OS
Calendar sync across providers
Email integration
Multi-device sync
Fine-tuned models
Shared memories
Multiple users
Roadmap
I would organize development into these milestones:
M0 – Repository & Architecture

Monorepo setup.
Clean Architecture skeleton.
Gradle modules.
CI.
Coding standards.
M1 – Assistant Core

Shared domain models.
Command bus.
Tool interfaces.
Dependency injection.
Logging.
M2 – Memory Engine

Room schema.
Memory lifecycle.
CRUD.
Confidence.
Relationships.
M3 – Task & Planning Engine

Task models.
Priorities.
Projects.
Planner.
M4 – Journal Engine

Journal entities.
Reflections.
Mood.
Weekly summaries.
M5 – Tool Engine (MCP)

Internal tool registry.
Alarm tool.
Reminder tool.
Calendar tool.
Notification tool.
M6 – Android App

Compose navigation.
Dashboard.
Task screens.
Journal screens.
Capture UI.
M7 – Local AI

STT.
llama.cpp integration.
Intent extraction.
Tool calling.
M8 – Retrieval

Embeddings.
Vector search.
Knowledge graph.
Memory ranking.
M9 – Agent Layer

Planner Agent.
Memory Agent.
Reflection Agent.
Retrieval Agent.
Tool Agent.
M10 – Intelligence

Weekly retrospectives.
Habit insights.
Adaptive planning.