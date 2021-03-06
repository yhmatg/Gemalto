package com.esimtek.esim

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.widget.Toast
import com.esimtek.esim.model.LoggedBean
import com.esimtek.esim.model.LoginBean
import com.esimtek.esim.model.ResultBean
import com.esimtek.esim.model.UserManagerBean
import com.esimtek.esim.network.Api
import com.esimtek.esim.network.HttpClient
import com.esimtek.esim.util.EncryptionUtil
import com.esimtek.esim.util.PreferenceUtil
import com.esimtek.esim.util.RegexUtil
import kotlinx.android.synthetic.main.activity_login.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : BaseActivity(), PasswordModifyFragment.Listener {

    lateinit var userName: String

    override fun onModifyClicked(password: String) {
        val unEncryptBuffer = StringBuffer()
        unEncryptBuffer.append(EncryptionUtil.SECRET_KEY).append(HttpClient.staffId).append(userName).append(password)
        val passwordBuffer = StringBuffer()
        unEncryptBuffer.toList().sorted().forEach { passwordBuffer.append(it) }
        val userManagerBean = UserManagerBean(userName, EncryptionUtil.encrypt(passwordBuffer.toString(), "MD5"), 2)
        userManager(userManagerBean)
    }

    private var scene by PreferenceUtil("scene", "main")
    //add 20191227 start
    private var savedName by PreferenceUtil("username", "")
    private var savedpassword  =  PreferenceUtil("scene", "").getSharePreferences("userpassword","")
    //add 20191227 end

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        set.setOnClickListener { startActivity(Intent(this, SetActivity::class.java)) }
        login.setOnClickListener {
            userName = userNameEditText.text.toString().trim()
            if (userName.isEmpty()) {
                if (BuildConfig.DEBUG) login("staff3", "staff@1234567890")
                else Toast.makeText(this@LoginActivity, "请输入用户名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val password = passwordEditText.text.toString().trim()
            if (!RegexUtil.isPassword(password)) {
                Toast.makeText(this@LoginActivity, "密码不符合规范", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            login(userName, password)
        }
        //add 20191227 start
        userNameEditText.text = Editable.Factory.getInstance().newEditable(savedName);
        //passwordEditText.text = Editable.Factory.getInstance().newEditable(savedpassword);
        //add 20191227 end
    }

    private fun login(userName: String, password: String) {
        hudDialog.show()
        val unEncryptBuffer = StringBuffer()
        unEncryptBuffer.append(EncryptionUtil.SECRET_KEY).append(HttpClient.staffId).append(userName).append(password)
        val passwordBuffer = StringBuffer()
        unEncryptBuffer.toList().sorted().forEach { passwordBuffer.append(it) }
        val loginBean = LoginBean(userName, EncryptionUtil.encrypt(passwordBuffer.toString(), "MD5"))
        HttpClient().provideRetrofit().create(Api::class.java).login(loginBean).enqueue(object : Callback<LoggedBean> {
            override fun onResponse(call: Call<LoggedBean>, response: Response<LoggedBean>) {
                hudDialog.dismiss()
                if (response.body()?.isSuccess == true) {
                    EsimApplication.instance.logged = response.body()
                    //去除每月密码到期更新
                   /* val isPasswordExpire = response.body()?.data?.isPasswordExpire ?: false
                    if (isPasswordExpire) {
                        PasswordModifyFragment().show(supportFragmentManager, "PasswordModifyDialog")
                        return
                    }*/
                    //1005 更换设备登录
                    if (response.body()?.code == 1005) {
                        Toast.makeText(this@LoginActivity, response.body()?.msg, Toast.LENGTH_SHORT).show()
                    }
                    //add 20191227 start
                    savedName = userName;
                    //savedpassword = password;
                    PreferenceUtil("scene", "").putSharePreferences("userpassword",password)
                    //add 20191227 end
                    when (EsimApplication.instance.logged?.data?.userType) {
                        "admin" -> {
                            startActivity(Intent(this@LoginActivity, UserManageActivity::class.java))
                            finish()
                        }
                        else -> {
                            val intent = when (scene) {
                                "hotStamping" -> Intent(this@LoginActivity, HotStampingScanActivity::class.java)
                                "implantation" -> Intent(this@LoginActivity, ImplantationScanActivity::class.java)
                                "packageLine" -> Intent(this@LoginActivity, PackageScanActivity::class.java)
                                else -> Intent(this@LoginActivity, MainActivity::class.java)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this@LoginActivity, response.body()?.msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoggedBean>, t: Throwable) {
                hudDialog.dismiss()
                t.printStackTrace()
                Toast.makeText(this@LoginActivity, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun userManager(bean: UserManagerBean) {
        hudDialog.show()
        HttpClient().provideRetrofit().create(Api::class.java).userManager(bean).enqueue(object : Callback<ResultBean> {
            override fun onResponse(call: Call<ResultBean>, response: Response<ResultBean>) {
                hudDialog.dismiss()
                if (response.body()?.isSuccess == true) {
                    Toast.makeText(this@LoginActivity, getString(R.string.modify_user_success), Toast.LENGTH_SHORT).show()
                    passwordEditText.setText("")
                } else {
                    Toast.makeText(this@LoginActivity, response.body()?.msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResultBean>, t: Throwable) {
                hudDialog.dismiss()
                t.printStackTrace()
                Toast.makeText(this@LoginActivity, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
            }
        })
    }
}
