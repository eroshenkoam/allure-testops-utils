package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.AccountAuthority;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface AdminAccountService {

    @POST("api/admin/account/{id}/role")
    Call<ResponseBody> setRole(final @Path("id") Long id, @Body AccountAuthority authority);

}
