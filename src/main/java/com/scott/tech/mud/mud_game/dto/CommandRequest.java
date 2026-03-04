package com.scott.tech.mud.mud_game.dto;

import java.util.List;

/**
 * Inbound message from the client over WebSocket.
 *
 * Two supported formats:
 *   Structured : { "command": "go", "args": ["north"] }
 *   Natural    : { "input": "head towards the north" }  ← resolved by AiIntentResolver
 *
 * When {@code input} is present and {@code command} is absent, the handler
 * routes the message through the AI intent resolver before processing.
 */
public class CommandRequest {

    private String command;
    private List<String> args;
    /** Raw natural language input – mutually exclusive with command/args. */
    private String input;
    /** Reconnect token sent by the client after a page refresh to skip re-authentication. */
    private String reconnectToken;

    public CommandRequest() {}

    public String getCommand()                        { return command; }
    public List<String> getArgs()                     { return args; }
    public String getInput()                          { return input; }
    public String getReconnectToken()                 { return reconnectToken; }

    public boolean isNaturalLanguage()                { return command == null && input != null && !input.isBlank(); }

    public void setCommand(String command)            { this.command = command; }
    public void setArgs(List<String> args)            { this.args = args; }
    public void setInput(String input)                { this.input = input; }
    public void setReconnectToken(String reconnectToken) { this.reconnectToken = reconnectToken; }
}
