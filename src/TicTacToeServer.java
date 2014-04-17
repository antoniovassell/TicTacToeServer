import java.awt.BorderLayout;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class TicTacToeServer extends JFrame{
	private JTextArea outputArea;
	private ArrayList< Game > games;
	private ArrayList< Player > players;
	private ServerSocket server;
	private final static int PLAYER_X = 0;
	private final static int PLAYER_0 = 1;
	private final static String[] MARKS = {"X", "0"};
	private ExecutorService runGame;
	private Socket connection;
	
	public TicTacToeServer(){
		super("Tic-Tac-Toe Server");
		
		runGame = Executors.newCachedThreadPool();
		
		players = new ArrayList< Player >();
		games = new ArrayList< Game >();
		
		try{
			server = new ServerSocket(12345,2);
		}catch(IOException ioException){
			ioException.printStackTrace();
			System.exit(1);
		}
		outputArea = new JTextArea();
		
		add(outputArea, BorderLayout.CENTER);
		outputArea.setText("Server awaiting connections\n");
		
		setSize(300,300);
		setVisible(true);
	}
	
	public void execute(){
		try {
			while (true) {
				connection = server.accept();
				System.out.println("Someone connected");
				System.out.println(players.size());
				players.add(players.size(), new Player(connection));
				runGame.execute(players.get(players.size() - 1));
			}
		} catch (IOException ioException) {
			ioException.printStackTrace();
			System.exit(1);
		}
	}
	
	private void displayMessage(final String messageToDisplay){
		SwingUtilities.invokeLater(
			new Runnable(){
				public void run(){
					outputArea.append(messageToDisplay);
				}
			}
		);
	}
	
	public Game getGame(String gameName) {
		Game game;
		for (int i = 0; i < games.size(); i++) {
			game = games.get(i);
			if (game.getName().equals(gameName)) {
				return game;
			}
		}
		return null;
	}
	
	private class Game {
		private int currentPlayer;
		private ArrayList< Player > players;
		private String name;
		private String[] board;
		private Condition otherPlayerConnected;
		private Condition otherPlayerTurn;
		private Lock gameLock;
		
		public Game(String name) {
			this.name = name;
			this.board = new String[9];
			gameLock = new ReentrantLock();
			currentPlayer = PLAYER_X;
			this.players = new ArrayList< Player >();
			otherPlayerConnected = gameLock.newCondition();
			otherPlayerTurn = gameLock.newCondition();
			
			for(int i= 0;i<9;i++){
				board[i] = new String("");
			}
		}
		
		public String getName() {
			return this.name;
		}
		
		public boolean validateAndMove(int location, int player){
			while(player != currentPlayer){
				gameLock.lock();
				try{
					otherPlayerTurn.await();
				}catch(InterruptedException exception){
					exception.printStackTrace();
				}finally{
					gameLock.unlock();
				}
			}
			if(!isOccupied(location)){
				board[location] = MARKS[currentPlayer];
				currentPlayer = (currentPlayer + 1) %2;
				
				this.players.get(currentPlayer).otherPlayerMoved(location);
				
				gameLock.lock();
				try{
					otherPlayerTurn.signal();
				}finally{
					gameLock.unlock();
				}
				return true;
			}else
				return false;
		}
		
		public boolean isOccupied(int location) {
			if(board[location].equals(MARKS[PLAYER_X])||board[location].equals(MARKS[PLAYER_0])){
				return true;
			}else
				return false;
		}
		
		public int getTotalPlayers() {
			return this.players.size();
		}
		
		public String addPlayer(Player player) {
			this.players.add(this.players.size(), player);
			
			if (this.players.size() == 2) {
				gameLock.lock();
				try{
					this.players.get(PLAYER_X).setSuspended(false);
					otherPlayerConnected.signal();
				}finally{
					gameLock.unlock();
				}
			}
			return MARKS[this.players.size() - 1];
		}
		
		private boolean matchDiagonally(String playerMark) {
			int match;
			match = 0;
			for (int i = 0; i < board.length; i += 4) {
				if (!playerMark.equals(board[i])) {
					break;
				} else {
					match++;
				}
			}
			if (match == 3) {
				return true;
			}
			
			match = 0;
			for (int i = 2; i <= 6; i += 2) {
				if (!playerMark.equals(board[i])) {
					break;
				} else {
					match++;
				}
			}
			if (match == 3) {
				System.out.println("Dia Won");
				return true;
			}
			
			return false;
		}
		
		private boolean matchVertically(String playerMark) {
			int match;
			match = 0;
			
			for (int i = 0; i < 3; i++) {
				match = 0;
				int max = i + 6;
				for (int x = i; x <= max; x += 3) {
					if (!playerMark.equals(board[x])) {
						System.out.println(playerMark);
						break;
					} else {
						System.out.println("FINALLY");
						match++;
					}
				}
				if (match == 3) {
					System.out.println("Vertical Won");
					return true;
				}
			}
			return false;
		}
		
		private boolean matchHorizontally(String playerMark) {
			return false;
		}
		
		public boolean calculateWin(String playerMark) {
			if (matchDiagonally(playerMark) || matchVertically(playerMark) || matchHorizontally(playerMark)) {
				return true;
			}
			return false;
		}
		
		public boolean isGameOver(){
			return false;
		}
	}
	
	private class Player implements Runnable{
		private Socket connection;
		private Scanner input;
		private Formatter output;
		private String playerName;
		private int playerNumber;
		private String mark;
		private boolean suspended = true;
		private Game game;
		
		public Player(Socket socket) {
			connection = socket;
			try{
				input = new Scanner(connection.getInputStream());
				output = new Formatter(connection.getOutputStream());
			}catch(IOException ioException){
				ioException.printStackTrace();
				System.exit(1);
			}
		}
		
		public void otherPlayerMoved(int location) {
			output.format("Opponent moved\n");
			output.format("%d\n", location);
			output.flush();
		}
		
		public void run() {
			String gameName = new String();
			try {
				this.playerName = input.nextLine();
				gameName = input.nextLine();
				game = getGame(gameName);
				
				if (game == null) {
					game = new Game(gameName);
					games.add(game);
				}
				
				if (game.getTotalPlayers() < 2) {
					mark = game.addPlayer(this);
					
					if (mark == "X") {
						this.playerNumber = 0;
					} else {
						this.playerNumber = 1;
					}
					
					output.format("%s\n", "Success");
					output.flush();
					
					displayMessage("Player "+ mark + " connected\n");
					output.format("%s\n",mark);
					output.flush();
					
					if(mark == "X"){
						output.format("%s\n%s", "Player X connected","Waiting for another player\n");
						output.flush();
						
						game.gameLock.lock();
						System.out.println("Stuck in limbo");
						try{
							while(suspended){
								System.out.println("Stuck in limbo");
								game.otherPlayerConnected.await();
							}
						}catch(InterruptedException exception){
							exception.printStackTrace();
						}finally{
							game.gameLock.unlock();
						}
						
						output.format("Other player connected. Your move.\n");
						output.flush();
					}else{
						output.format("Player 0 connected, please wait\n");
						output.flush();
					}
					
					while (!game.isGameOver()) {
						int location = 0;
						if (input.hasNext()) {
							location = input.nextInt();
						}
						if (game.validateAndMove(location, playerNumber)) {
							displayMessage("\nlocation: "+ location);
							output.format("Valid move.\n");
							output.flush();
							
							if (game.calculateWin(MARKS[playerNumber])) {
								output.format("You won!.\n");
								output.flush();
							}
						} else {
							output.format("Invalid move, try again\n");
							output.flush();
						}
					}
				} else {
					output.format("%s\n", "Occupied\n");
					output.flush();
				}
			}finally{
				try {
					connection.close();
				} catch(IOException ioException) {
					ioException.printStackTrace();
					System.exit(1);
				}
			}
		}
		public void setSuspended(boolean status){
			suspended = status;
		}
	}
}

