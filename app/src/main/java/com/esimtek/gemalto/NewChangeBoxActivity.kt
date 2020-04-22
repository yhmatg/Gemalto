package com.esimtek.gemalto

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import com.esimtek.gemalto.adapter.ESLListAdapter
import com.esimtek.gemalto.model.*
import com.esimtek.gemalto.network.Api
import com.esimtek.gemalto.network.HttpClient
import com.esimtek.gemalto.util.BeeperUtil
import com.esimtek.gemalto.util.ZxingUtil
import com.nativec.tools.ModuleManager
import com.scanner1d.ODScannerHelper
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_new_change_box.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class NewChangeBoxActivity : BaseActivity() {

    private val scanner: ODScannerHelper = ODScannerHelper.getDefaultHelper()
    private val postTransferList: ArrayList<String> = ArrayList()

    private val adapter = ESLListAdapter()

    private var obScanner: Observer = Observer { _, value ->
        if (value is String)
            disposable.add(Observable.just(value)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        BeeperUtil.beep(BeeperUtil.BEEPER_SHORT)
                        if(it.length > 6){
                            getWrokNumByPlCodeOrESL(it)
                        }else if(it.length == 6){
                            adapter.addItem(it)
                            esl_num_scan.setText(adapter.list.size.toString())
                        } else {
                            Toast.makeText(this, "条码格式错误", Toast.LENGTH_SHORT).show()
                        }
                    })
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_change_box)
        ModuleManager.newInstance().uhfStatus = false
        ModuleManager.newInstance().scanStatus = true
        scanner.setRunFlag(false)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        next_submit.setOnClickListener {
            if(workNumIsEmpty() || eslkNumIsEmpty()){
                Toast.makeText(this@NewChangeBoxActivity, "请先扫描标签获取工单信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (adapter.list.isEmpty()) {
                Toast.makeText(this@NewChangeBoxActivity, "未扫描到标签，请重新扫描后再试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (adapter.list.size != esl_num.text.toString().toInt()) {
                Toast.makeText(this@NewChangeBoxActivity, "请扫描工单号要求的标签数再重试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            postTransferList.clear()
            postTransferList.addAll(adapter.list)
            relateESLAndPL(NewRelateBean(postTransferList,work_num.text.toString()))
        }

        clear_work_num.setOnClickListener{
            work_num.text = ""
            esl_num.text = ""
            esl_num_scan.text = ""
            adapter.clear()
            postTransferList.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        scanner.registerObserver(obScanner)
    }

    override fun onPause() {
        super.onPause()
        scanner.unRegisterObserver(obScanner)
    }

    //换盒子操作，网络绑定ESL和纸质标签
    private fun relateESLAndPL(bean: NewRelateBean) {
        hudDialog.show()
        HttpClient().provideRetrofit().create(Api::class.java).relateESLAndPL(bean).enqueue(object : Callback<ResultBean> {
            override fun onResponse(call: Call<ResultBean>, response: Response<ResultBean>) {
                hudDialog.dismiss()
                if (response.body()?.isSuccess == true) {
                    Toast.makeText(this@NewChangeBoxActivity, getString(R.string.box_change_success), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@NewChangeBoxActivity, response.body()?.msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResultBean>, t: Throwable) {
                hudDialog.dismiss()
                t.printStackTrace()
                Toast.makeText(this@NewChangeBoxActivity, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getWrokNumByPlCodeOrESL(plCodeOrESL: String) {
        hudDialog.show()
        HttpClient().provideRetrofit().create(Api::class.java).getWrokNumByPlCodeOrESL(plCodeOrESL).enqueue(object : Callback<workNumberBean> {
            override fun onResponse(call: Call<workNumberBean>, response: Response<workNumberBean>) {
                hudDialog.dismiss()
                if (response.body()?.isSuccess == true) {
                    Toast.makeText(this@NewChangeBoxActivity, getString(R.string.get_work_num_success), Toast.LENGTH_SHORT).show()
                    work_num.setText(response.body()?.data?.workNumber)
                    esl_num.setText(response.body()?.data?.eslNum.toString())
                } else {
                    Toast.makeText(this@NewChangeBoxActivity, response.body()?.msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<workNumberBean>, t: Throwable) {
                hudDialog.dismiss()
                t.printStackTrace()
                Toast.makeText(this@NewChangeBoxActivity, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun workNumIsEmpty() :Boolean{

        return work_num.text.toString().isEmpty()
    }

    private fun eslkNumIsEmpty() :Boolean{

        return esl_num.text.toString().isEmpty()
    }
}