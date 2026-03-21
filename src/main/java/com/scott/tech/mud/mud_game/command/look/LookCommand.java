package com.scott.tech.mud.mud_game.command.look;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

public class LookCommand implements GameCommand {

    private final String target;
    private final LookValidator lookValidator;
    private final LookService lookService;

    public LookCommand(String target, GameSessionManager sessionManager) {
        this(target, new LookValidator(sessionManager), new LookService(sessionManager, null));
    }

    public LookCommand(String target, GameSessionManager sessionManager, QuestService questService) {
        this(target, new LookValidator(sessionManager), new LookService(sessionManager, questService));
    }

    public LookCommand(String target, LookValidator lookValidator, LookService lookService) {
        this.target = target;
        this.lookValidator = lookValidator;
        this.lookService = lookService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        LookValidationResult validation = lookValidator.validate(session, target);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        return lookService.buildResult(session, validation);
    }
}
