package com.project.catxi.chat.dto;

import java.util.List;

public record ParticipantsUpdateMessage(
	Long roomId,
	List<ParticipantBrief> participants
) {}
