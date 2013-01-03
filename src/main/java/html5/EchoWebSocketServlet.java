package html5;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 */
@WebServlet(name = "echo", urlPatterns = "/echo", asyncSupported = true)
public class EchoWebSocketServlet extends WebSocketServlet {

    private static final int HEIGHT = 600;
    private static final int WIDTH = 800;
    private static final int PLAYER_HEIGHT = 80;

    private List<MyMessageHandler> playing = new ArrayList<MyMessageHandler>();
    private MyMessageHandler waiting;

    @Override
    public void init() throws ServletException {
        super.init();
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        for (MyMessageHandler handler : new ArrayList<MyMessageHandler>(playing)) {
                            handler.ballX += handler.dx;
                            handler.ballY += handler.dy;
                            if (handler.ballY < 0) {
                                handler.ballY = -handler.ballY;
                                handler.dy = -handler.dy;
                            }
                            if (handler.ballY > HEIGHT) {
                                handler.ballY = 2 * HEIGHT - handler.ballY;
                                handler.dy = -handler.dy;
                            }
                            if (handler.ballX < 30 && handler.dx < 0) {
                                int dy = handler.ballY - (handler.first ? handler.y : handler.other.y) - PLAYER_HEIGHT / 2;
                                if (Math.abs(dy) < PLAYER_HEIGHT / 2) {
                                    handler.dx = (int) (Math.random() * (1 -1f* Math.abs(dy) / PLAYER_HEIGHT) * 15) + 5;
                                    System.out.println((1-1f*Math.abs(dy)/PLAYER_HEIGHT)*20);
                                } else {
                                    (handler.first ? handler.other : handler).points++;
                                    handler.initBall();
                                }
                            }
                            if (handler.ballX > WIDTH - 40 && handler.dx > 0) {
                                int dy = handler.ballY - (handler.first ? handler.other.y : handler.y) - PLAYER_HEIGHT / 2;
                                if (Math.abs(dy) < PLAYER_HEIGHT / 2) {
                                    handler.dx = -((int) (Math.random() * (1 -1f* Math.abs(dy) / PLAYER_HEIGHT) *15) + 5);
                                    System.out.println((1-1f*Math.abs(dy)/PLAYER_HEIGHT)*20);
                                } else {
                                    (handler.first ? handler : handler.other).points++;
                                    handler.initBall();
                                }
                            }
                            try {
                                handler.sendMessages();
                            } catch (IOException e) {

                            }
                        }
                    }
                }, 0, 20);
    }

    private class MyMessageHandler implements WebSocket.OnTextMessage {
        private boolean first;
        private Connection connection;
        private MyMessageHandler other;
        private int y, ballX, ballY, dx, dy;
        private int points;

        public void onOpen(Connection connection) {
            this.connection = connection;
            if (waiting == null || !waiting.connection.isOpen()) {
                System.out.println("new solo " + connection);
                waiting = this;
                first = true;
            } else {
                System.out.println("new pair " + connection);
                other = waiting;
                other.other = this;
                waiting = null;
                other.initBall();
                playing.add(other);
            }
        }

        public void initBall() {
            if (points == 9 || other.points == 9) {
                playing.remove(first ? this : other);
            } else {
                ballX = WIDTH / 2;
                ballY = HEIGHT / 2;
                dx = (int) (Math.random() * 7) + 6;
                if (Math.random() > .5) {
                    dx = -dx;
                }
                dy = (int) (Math.random() * 7) + 6;
                if (Math.random() > .5) {
                    dy = -dy;
                }
            }
        }

        public void onMessage(String data) {
            try {
                if (data.startsWith("pos:")) {
                    y = Integer.parseInt(data.substring(4));
                }
                if (other != null) {
                    sendMessages();
                }
            } catch (IOException e) {
                if (!connection.isOpen()) {
                    leave();
                }
                if (!other.connection.isOpen()) {
                    other.leave();
                }
                e.printStackTrace();
            }
        }

        public void sendMessages() throws IOException {
            if (first) {
                String msg = y + "," + other.y + "," + ballX + "," + ballY + "," + points + "," + other.points;
                connection.sendMessage("f," + msg);
                other.connection.sendMessage("l," + msg);
            } else {
                other.sendMessages();
            }
        }

        public void onClose(int closeCode, String message) {
            leave();
        }

        private void leave() {
            System.out.println("leave " + connection);
            playing.remove(first ? this : other);
            if (other != null) {
                other.other = null;
                if (waiting == null) {
                    waiting = other;
                    if (other != null) {
                        other.first = true;
                    }
                } else {
                    other.connection = waiting.connection;
                    waiting.connection = other.connection;
                    //TODO
                    waiting = null;
                }
            }
        }
    }


    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        return new MyMessageHandler();
    }
}
