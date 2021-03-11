package gui;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;

public interface IConnectSipToGUI {
    public void handleCallInvitation(RequestEvent requestEvent, ServerTransaction serverTransaction);

    public void handleBye();
}
