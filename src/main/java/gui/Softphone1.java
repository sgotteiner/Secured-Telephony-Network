package gui;

import gov.nist.javax.sip.header.Contact;
import sip.Client;

import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Softphone1 {

    private JPanel panel;
    private static JFrame frame;
    private JTextField txtCallByName;
    private JButton btnCallHangupAnswer;
    private boolean isInCall = false, isAnswer = false;
    private JTextField txtUsername;
    private JTextField txtDisplayName;
    private JTextField txtDomainName;
    private JTextField txtMyIP;
    private JTextField txtServerIP;
    private JTextField txtMyPort;
    private JTextField txtServerPort;
    private JButton btnRegister;
    private boolean isRegistered = false;
    private JTextArea terminal;
    private JScrollPane scrollPane;

    private IConnectSipToGUI iConnectSipToGUI;
    private RequestEvent requestEvent;
    private ServerTransaction serverTransaction;

    public Softphone1(Client client) {

        txtCallByName.setText("Client2NameDisplay");
        txtUsername.setText("Client1Name");
        txtDisplayName.setText("Client1NameDisplay");
        txtDomainName.setText("port5060");
        txtMyIP.setText("127.0.0.1");
        txtMyPort.setText("5060");
        txtServerIP.setText("127.0.0.1");
        txtServerPort.setText("5080");
        scrollPane.setPreferredSize(new Dimension(getPanel().getWidth(), 150));

        iConnectSipToGUI = new IConnectSipToGUI() {
            @Override
            public void handleCallInvitation(RequestEvent requestEvent, ServerTransaction serverTransaction) {
                btnCallHangupAnswer.setText("Answer");
                isAnswer = true;
                Softphone1.this.requestEvent = requestEvent;
                Softphone1.this.serverTransaction = serverTransaction;
            }

            @Override
            public void handleBye() {
                btnCallHangupAnswer.setText("Call");
                isInCall = false;
            }

            @Override
            public void handleRegistration(boolean isRegistered) {
                Softphone1.this.isRegistered = isRegistered;
                if (isRegistered)
                    frame.getContentPane().setBackground(Color.green);
                else frame.getContentPane().setBackground(Color.red);
            }

            @Override
            public void printMessage(String message) {
                terminal.append(message + "\n");
            }
        };

        btnRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.register(txtUsername.getText(), txtDisplayName.getText(), txtDomainName.getText(),
                        txtMyIP.getText(), Integer.parseInt(txtMyPort.getText()),
                        txtServerIP.getText() + ":" + txtServerPort.getText(), iConnectSipToGUI);
            }
        });
        btnCallHangupAnswer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isRegistered){
                    terminal.append("Must register to call" + "\n");
                    return;
                }

                isInCall = !isInCall;

                if (isInCall) {
                    btnCallHangupAnswer.setText("Hang up");
                    if (isAnswer) {
                        client.responseToInvite(requestEvent, serverTransaction);
                        isAnswer = false;
                    } else {
                        client.invite(txtCallByName.getText());
                    }
                } else {
                    client.sendBye();
                }
            }
        });
    }

    public JPanel getPanel() {
        return panel;
    }

    public static void main(String[] args) {

        frame = new JFrame("Softphone 1");
        frame.setContentPane(new Softphone1(new Client()).getPanel());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.getContentPane().setBackground(Color.red);
        frame.setVisible(true);
    }
}
