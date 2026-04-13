# Makefile — CE/CS 4390 Math Server Project
# Team: Gaurang Dhanani & Vedant Deshmukh — Spring 2026
#
# Targets:
#   make         → compile all Java files
#   make server  → run the server on default port 6789
#   make client  → run a client connecting to localhost:6789
#   make clean   → remove compiled .class files and server.log

JAVAC  = javac
JAVA   = java

PORT   = 6789
HOST   = 127.0.0.1

# All source files (order matters — Protocol first, then dependents)
SOURCES = Protocol.java \
          MathEvaluator.java \
          ServerLogger.java \
          RequestProcessor.java \
          ClientHandler.java \
          MathServer.java \
          MathClient.java

.PHONY: all server client clean

# ── Compile all ──────────────────────────────────────────────────────────
all:
	$(JAVAC) $(SOURCES)
	@echo "Compilation successful."

# ── Run server ───────────────────────────────────────────────────────────
server: all
	$(JAVA) MathServer $(PORT)

# ── Run client ───────────────────────────────────────────────────────────
client: all
	$(JAVA) MathClient $(HOST) $(PORT)

# ── Clean build artifacts ────────────────────────────────────────────────
clean:
	rm -f *.class server.log
	@echo "Cleaned."
