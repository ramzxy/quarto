# Sequence Diagrams

## 1. Client Connection & Login

```mermaid
sequenceDiagram
    actor User as User
    participant App as ClientApplication
    participant GC as GameClient
    participant Conn as ServerConnection
    participant HP as HumanPlayer
    participant View as TUI
    participant CH as ClientHandler
    participant S as Server
    participant GM as GameManager

    User->>App: Start application
    App->>View: new TUI()
    App->>HP: new HumanPlayer("Alice", view)
    App->>GC: new GameClient(player, view)
    
    User->>GC: connect("localhost", 5000)
    activate GC
    GC->>Conn: new ServerConnection()
    GC->>Conn: connect("localhost", 5000)
    activate Conn
    Conn->>S: TCP connect
    activate S
    S->>CH: new ClientHandler(socket)
    S->>S: clients.add(CH)
    Note over S: Spawns new thread for CH
    deactivate S
    Conn-->>GC: connected
    deactivate Conn
    
    GC->>GC: handshake()
    GC->>Conn: sendMessage("HELLO~Quarto")
    Conn->>CH: "HELLO~Quarto"
    CH->>Conn: "HELLO~Quarto"
    Conn-->>GC: "HELLO~Quarto"
    
    GC->>View: requestUsername()
    View->>User: "Enter username:"
    User->>View: "Alice"
    View-->>GC: "Alice"
    
    GC->>Conn: sendMessage("LOGIN~Alice")
    Conn->>CH: "LOGIN~Alice"
    activate CH
    CH->>GM: registerUsername("Alice")
    activate GM
    GM-->>CH: true (success)
    deactivate GM
    CH->>Conn: "LOGIN"
    deactivate CH
    Conn-->>GC: "LOGIN"
    
    Note over GC: Login successful!
    deactivate GC
```

---

## 2. Queue Command Handling

```mermaid
sequenceDiagram
    actor P1 as Player 1 (Alice)
    participant GC1 as GameClient (P1)
    participant Conn1 as ServerConnection (P1)
    participant CH1 as ClientHandler (P1)
    participant S as Server
    participant GM as GameManager
    participant Q as WaitingQueue
    participant Game as Game
    participant CH2 as ClientHandler (P2)
    participant Conn2 as ServerConnection (P2)
    participant GC2 as GameClient (P2)
    actor P2 as Player 2 (Bob)

    Note over GM: Initial State: Queue is empty

    P1->>GC1: queue()
    GC1->>Conn1: sendMessage("QUEUE")
    Conn1->>CH1: "QUEUE"
    activate CH1
    CH1->>CH1: handleProtocolMessage("QUEUE")
    CH1->>GM: queueForGame(CH1)
    activate GM
    GM->>Q: add(CH1)
    activate Q
    Q-->>GM: void
    deactivate Q
    GM->>GM: checkForMatch()
    Note over GM: Queue has 1 player.<br/>No match possible.
    GM-->>CH1: void
    deactivate GM
    deactivate CH1

    Note over GM: Player 1 is waiting in the queue...

    P2->>GC2: queue()
    GC2->>Conn2: sendMessage("QUEUE")
    Conn2->>CH2: "QUEUE"
    activate CH2
    CH2->>CH2: handleProtocolMessage("QUEUE")
    CH2->>GM: queueForGame(CH2)
    activate GM
    GM->>Q: add(CH2)
    activate Q
    Q-->>GM: void
    deactivate Q
    
    GM->>GM: checkForMatch()
    Note over GM: Queue has 2 players.<br/>Match found!
    
    GM->>Q: poll()
    activate Q
    Q-->>GM: CH1
    deactivate Q
    GM->>Q: poll()
    activate Q
    Q-->>GM: CH2
    deactivate Q

    GM->>Game: new Game("Alice", "Bob")
    activate Game
    Game->>Game: initializeBoard()
    Game->>Game: selectRandomFirstPiece()
    Game-->>GM: Game instance
    deactivate Game

    GM->>CH1: setCurrentGame(Game)
    GM->>CH2: setCurrentGame(Game)
    
    par Notify P1
        GM->>CH1: sendNewGame("Alice", "Bob")
        CH1->>Conn1: "NEWGAME~Alice~Bob"
        Conn1-->>GC1: "NEWGAME~Alice~Bob"
        GC1->>GC1: opponentName = "Bob"
    and Notify P2
        GM->>CH2: sendNewGame("Alice", "Bob")
        CH2->>Conn2: "NEWGAME~Alice~Bob"
        Conn2-->>GC2: "NEWGAME~Alice~Bob"
        GC2->>GC2: opponentName = "Alice"
    end
    
    deactivate GM
    deactivate CH2

    Note over Game: Game Started!<br/>First piece selected by server.
```

---

## 3. Move Command Handling (Full Client-Server Flow)

```mermaid
sequenceDiagram
    actor User1 as User (Alice)
    participant View1 as TUI (P1)
    participant HP1 as HumanPlayer (P1)
    participant GC1 as GameClient (P1)
    participant Conn1 as ServerConnection (P1)
    participant CH1 as ClientHandler (P1)
    participant Game as Game (Server)
    participant Board as Board
    participant CH2 as ClientHandler (P2)
    participant Conn2 as ServerConnection (P2)
    participant GC2 as GameClient (P2)
    participant HP2 as HumanPlayer (P2)
    participant View2 as TUI (P2)
    actor User2 as User (Bob)

    Note over Game: State: Alice's Turn<br/>Piece to Place: ID 5

    rect rgb(230, 245, 255)
        Note over GC1: It's my turn!
        GC1->>HP1: determineMove(localGame)
        activate HP1
        HP1->>View1: requestMove(game)
        activate View1
        View1->>User1: "Enter position and next piece:"
        User1->>View1: "3 9"
        View1-->>HP1: Move(3, 9)
        deactivate View1
        HP1-->>GC1: Move(3, 9)
        deactivate HP1
    end
    
    GC1->>Conn1: sendMessage("MOVE~3~9")
    Conn1->>CH1: "MOVE~3~9"
    
    activate CH1
    CH1->>CH1: handleProtocolMessage("MOVE~3~9")
    Note over CH1: Parse string to Move object
    
    CH1->>Game: doMove(Move(3, 9))
    activate Game
    
    Game->>Game: validateMove(Move)
    alt Invalid Move
        Game-->>CH1: throws InvalidMoveException
        CH1->>Conn1: "ERROR~Invalid move"
        Conn1-->>GC1: "ERROR~Invalid move"
        GC1->>View1: showMessage("Invalid move!")
    else Valid Move
        Game->>Board: setPiece(3, Piece(5))
        activate Board
        Board-->>Game: void
        deactivate Board
        
        Game->>Board: hasWinningLine()
        activate Board
        Board-->>Game: false
        deactivate Board
        
        Game->>Game: currentPieceToPlace = Piece(9)
        Game->>Game: switchTurn()
        
        Game->>Game: notifyListeners()
        
        par Notify P1 (confirmation)
            Game->>CH1: moveMade(Move(3, 9))
            CH1->>Conn1: "MOVE~3~9"
            Conn1-->>GC1: "MOVE~3~9"
            GC1->>GC1: localGame.doMove(Move(3, 9))
            GC1->>View1: displayGame(localGame)
        and Notify P2 (opponent's move)
            Game->>CH2: moveMade(Move(3, 9))
            activate CH2
            CH2->>Conn2: "MOVE~3~9"
            Conn2-->>GC2: "MOVE~3~9"
            GC2->>GC2: localGame.doMove(Move(3, 9))
            GC2->>View2: displayGame(localGame)
            deactivate CH2
        end
    end
    
    deactivate Game
    deactivate CH1

    rect rgb(255, 245, 230)
        Note over GC2: Now it's my turn!
        GC2->>HP2: determineMove(localGame)
        activate HP2
        HP2->>View2: requestMove(game)
        activate View2
        View2->>User2: "Enter position and next piece:"
        Note over User2: Bob makes their move...
        deactivate View2
        deactivate HP2
    end
```

---

## 4. AI Client Move (ComputerPlayer)

```mermaid
sequenceDiagram
    participant GC as GameClient
    participant CP as ComputerPlayer
    participant Strat as SmartStrategy
    participant Conn as ServerConnection
    participant CH as ClientHandler
    participant Game as Game (Server)

    Note over GC: It's AI's turn!
    
    GC->>CP: determineMove(localGame)
    activate CP
    CP->>Strat: computeMove(localGame)
    activate Strat
    Note over Strat: Runs minimax algorithm<br/>Evaluates positions...
    Strat-->>CP: Move(7, 12)
    deactivate Strat
    CP-->>GC: Move(7, 12)
    deactivate CP
    
    GC->>Conn: sendMessage("MOVE~7~12")
    Conn->>CH: "MOVE~7~12"
    activate CH
    CH->>Game: doMove(Move(7, 12))
    activate Game
    Game->>Game: validateMove()
    Game->>Game: applyMove()
    Game->>Game: notifyListeners()
    deactivate Game
    CH->>Conn: "MOVE~7~12"
    deactivate CH
    Conn-->>GC: "MOVE~7~12"
    
    Note over GC: Move confirmed, wait for opponent
```

---

## 5. Game End Handling

```mermaid
sequenceDiagram
    participant GC1 as GameClient (P1)
    participant Conn1 as ServerConnection (P1)
    participant CH1 as ClientHandler (P1)
    participant Game as Game (Server)
    participant GM as GameManager
    participant CH2 as ClientHandler (P2)
    participant Conn2 as ServerConnection (P2)
    participant GC2 as GameClient (P2)
    participant View1 as TUI (P1)
    participant View2 as TUI (P2)

    Note over Game: P1 places final piece...

    CH1->>Game: doMove(Move(15, -1))
    activate Game
    Note over Game: -1 means no next piece<br/>(placing last available piece)
    
    Game->>Game: validateMove()
    Game->>Game: applyMove()
    Game->>Game: checkWinCondition()
    Note over Game: QUARTO! 4 pieces align!
    
    Game->>Game: notifyListeners(GAME_OVER, winner=P1)
    
    par Notify P1
        Game->>CH1: gameFinished(Result.WIN)
        CH1->>Conn1: "GAMEOVER~VICTORY~Alice"
        Conn1-->>GC1: "GAMEOVER~VICTORY~Alice"
        GC1->>View1: showMessage("You won!")
    and Notify P2
        Game->>CH2: gameFinished(Result.LOSS)
        CH2->>Conn2: "GAMEOVER~VICTORY~Alice"
        Conn2-->>GC2: "GAMEOVER~VICTORY~Alice"
        GC2->>View2: showMessage("Alice won!")
    end
    
    deactivate Game
    
    Game->>GM: endGame(this)
    activate GM
    GM->>GM: activeGames.remove(game)
    GM->>CH1: setCurrentGame(null)
    GM->>CH2: setCurrentGame(null)
    deactivate GM
    
    Note over GC1,GC2: Players can now QUEUE again
```
