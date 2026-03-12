package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

public class SocialCommand implements GameCommand {

    private final SocialAction action;
    private final String targetArg;
    private final GameSessionManager sessionManager;
    private final SocialValidator socialValidator;
    private final SocialService socialService;

    public SocialCommand(SocialAction action, String targetArg, GameSessionManager sessionManager) {
        this(action, targetArg, sessionManager, new SocialValidator(), new SocialService());
    }

    public SocialCommand(SocialAction action,
                         String targetArg,
                         GameSessionManager sessionManager,
                         SocialValidator socialValidator,
                         SocialService socialService) {
        this.action = action;
        this.targetArg = targetArg == null ? null : targetArg.trim();
        this.sessionManager = sessionManager;
        this.socialValidator = socialValidator;
        this.socialService = socialService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        SocialValidationResult validation = socialValidator.validate(session, action, targetArg, sessionManager);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        return socialService.buildResult(session, action, validation);
    }
}
