package com.scott.tech.mud.mud_game.model;

public class Player {

    private final String id;
    private String name;
    private String currentRoomId;

    public Player(String id, String name, String startRoomId) {
        this.id            = id;
        this.name          = name;
        this.currentRoomId = startRoomId;
    }

    public String getId()            { return id; }
    public String getName()          { return name; }
    public String getCurrentRoomId() { return currentRoomId; }

    public void setName(String name)                  { this.name = name; }
    public void setCurrentRoomId(String currentRoomId){ this.currentRoomId = currentRoomId; }
}
