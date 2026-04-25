package com.joinai_support.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TicketLookupResponse {
    private boolean found;
    private String message;
    private List<TicketContextDTO> tickets = new ArrayList<>();
}
