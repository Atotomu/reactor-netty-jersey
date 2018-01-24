package com.atotomu.reactor.jersey.router;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author wangtong
 * @since 1.0
 */
@Path("/hot")
public class ServiceHot {

    @GET
    public String get() {
        return "hot";
    }

    @POST()
    @Path("/post")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserTest post(UserTest body) {
        System.out.println("body = " + body);
        return new UserTest("keke", 200, "li");
    }
}
