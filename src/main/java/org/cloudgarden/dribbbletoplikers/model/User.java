package org.cloudgarden.dribbbletoplikers.model;

import lombok.Value;

/**
 * Created by schernysh on 7/7/17.
 */
@Value
public class User {
    Integer followersCount;
    Integer shotsCount;
    String username;
}
