package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.Account;
import io.github.eroshenkoam.allure.client.dto.Authority;
import io.github.eroshenkoam.allure.client.dto.Page;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

@SuppressWarnings("PMD.LinguisticNaming")
public interface AccountService {

    /**
     * Returns the authorized user.
     */
    @GET("api/uaa/account/me")
    Call<Account> getAccount();

    @GET("api/uaa/account")
    Call<Page<Account>> getAccounts();

    @GET("api/uaa/account")
    Call<Page<Account>> getAccounts(
            @Query("query") String query,
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("api/uaa/account/{username}/find")
    Call<Account> findByUsername(@Path("username") String username);

    @POST("api/uaa/account/register")
    Call<Account> register(@Body Account account);

    @POST("api/uaa/account/{id}/authority")
    Call<ResponseBody> setAuthority(@Path("id") Long id, @Body List<Authority> authorities);
}
