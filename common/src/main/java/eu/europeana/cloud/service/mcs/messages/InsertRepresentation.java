/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package eu.europeana.cloud.service.mcs.messages;

import eu.europeana.cloud.service.mcs.messages.AbstractMessage;

/**
 * 
 * @author krystian
 */
public class InsertRepresentation extends AbstractMessage {

    public InsertRepresentation(String payload) {
	super(payload);
    }
}
