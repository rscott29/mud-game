package com.scott.tech.mud.mud_game.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a player's in-game profile.
 *
 * Stores the player's last known room so they resume where they left off.
 * Mapped to the {@code player_profiles} table (Flyway V1).
 */
@Entity
@Table(name = "player_profiles")
public class PlayerProfileEntity {

    @Id
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "current_room_id", nullable = false, length = 100)
    private String currentRoomId;

    @Column(name = "level", nullable = false)
    private int level = 1;

    @Column(name = "title", nullable = false, length = 100)
    private String title = "Adventurer";

    @Column(name = "race", nullable = false, length = 50)
    private String race = "Human";

    @Column(name = "class", nullable = false, length = 50)
    private String characterClass = "Adventurer";

    @Column(name = "pronouns_subject", nullable = false, length = 20)
    private String pronounsSubject = "they";

    @Column(name = "pronouns_object", nullable = false, length = 20)
    private String pronounsObject = "them";

    @Column(name = "pronouns_possessive", nullable = false, length = 20)
    private String pronounsPossessive = "their";

    @Column(name = "description")
    private String description;

    @Column(name = "health", nullable = false)
    private int health = 100;

    @Column(name = "max_health", nullable = false)
    private int maxHealth = 100;

    @Column(name = "mana", nullable = false)
    private int mana = 50;

    @Column(name = "max_mana", nullable = false)
    private int maxMana = 50;

    @Column(name = "movement", nullable = false)
    private int movement = 100;

    @Column(name = "max_movement", nullable = false)
    private int maxMovement = 100;

    @Column(name = "equipped_weapon_id", length = 100)
    private String equippedWeaponId;

    @Column(name = "recall_room_id", length = 100)
    private String recallRoomId;

    @Column(name = "experience", nullable = false)
    private int experience = 0;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PlayerProfileEntity() {}

    public PlayerProfileEntity(String username, String currentRoomId) {
        this.username      = username;
        this.currentRoomId = currentRoomId;
        this.lastSeenAt    = Instant.now();
    }

    public String  getUsername()          { return username; }
    public String  getCurrentRoomId()     { return currentRoomId; }
    public int     getLevel()             { return level; }
    public String  getTitle()             { return title; }
    public String  getRace()              { return race; }
    public String  getCharacterClass()    { return characterClass; }
    public String  getPronounsSubject()   { return pronounsSubject; }
    public String  getPronounsObject()    { return pronounsObject; }
    public String  getPronounsPossessive(){ return pronounsPossessive; }
    public String  getDescription()       { return description; }
    public int     getHealth()            { return health; }
    public int     getMaxHealth()         { return maxHealth; }
    public int     getMana()              { return mana; }
    public int     getMaxMana()           { return maxMana; }
    public int     getMovement()          { return movement; }
    public int     getMaxMovement()       { return maxMovement; }
    public String  getEquippedWeaponId()  { return equippedWeaponId; }
    public String  getRecallRoomId()      { return recallRoomId; }
    public int     getExperience()        { return experience; }
    public Instant getLastSeenAt()        { return lastSeenAt; }

    public void setCurrentRoomId(String currentRoomId)    { this.currentRoomId = currentRoomId; }
    public void setLevel(int level)                       { this.level = level; }
    public void setTitle(String title)                    { this.title = title; }
    public void setRace(String race)                      { this.race = race; }
    public void setCharacterClass(String characterClass)  { this.characterClass = characterClass; }
    public void setPronounsSubject(String pronounsSubject){ this.pronounsSubject = pronounsSubject; }
    public void setPronounsObject(String pronounsObject)  { this.pronounsObject = pronounsObject; }
    public void setPronounsPossessive(String pronounsPossessive) { this.pronounsPossessive = pronounsPossessive; }
    public void setDescription(String description)        { this.description = description; }
    public void setHealth(int health)                     { this.health = health; }
    public void setMaxHealth(int maxHealth)              { this.maxHealth = maxHealth; }
    public void setMana(int mana)                        { this.mana = mana; }
    public void setMaxMana(int maxMana)                  { this.maxMana = maxMana; }
    public void setMovement(int movement)                { this.movement = movement; }
    public void setMaxMovement(int maxMovement)          { this.maxMovement = maxMovement; }
    public void setEquippedWeaponId(String equippedWeaponId) { this.equippedWeaponId = equippedWeaponId; }
    public void setRecallRoomId(String recallRoomId)     { this.recallRoomId = recallRoomId; }
    public void setExperience(int experience)            { this.experience = experience; }
    public void setLastSeenAt(Instant lastSeenAt)         { this.lastSeenAt = lastSeenAt; }
}
