import javax.swing.JFrame;
public class TicTacToeTest {
	public static void main(String[] args) {
		TicTacToeServer application = new TicTacToeServer();
		application.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		application.execute();
	}
}
