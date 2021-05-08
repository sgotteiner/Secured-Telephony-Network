package gui;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;

public interface IConnectSipToGUI extends IMessageInGUI {
    public void handleCallInvitation(RequestEvent requestEvent, ServerTransaction serverTransaction);

    public void handleBye();

    public void handleRegistration(boolean isRegistered);
}
