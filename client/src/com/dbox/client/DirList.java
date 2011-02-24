package com.dbox.client;

import java.net.URL;

import com.dbox.client.Login.LoggedIn;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class DirList extends Activity
{   
    private ListView list;
    private DirListAdapter adapter;
    private String mHost;
    private String mUrl;
    private String mUsername;
    private String mPassword;
    private int mPort;
    private Resource[] ls;
    private ProgressDialog mProgressDialog;
    
	private class LsTask extends AsyncTask<String, Integer, Integer>
	{
		/**
		 * Executes task on a background thread.
		 */
		@Override
		protected Integer doInBackground(String... data)
		{
			ls = WebService.get(mUrl,mPort,mUsername,mPassword);
			return 1;
		}

		/**
		 * Called on the UI thread after doInBackground has finished.
		 */
		@Override
	    protected void onPostExecute(Integer result)
	    {
			onServerResponse();
	    }
	}

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dirlist);
        
        Bundle bundle = getIntent().getExtras();
        
        try
        {
	        mHost = bundle.getString("host");
	        mUsername = bundle.getString("username");
	        mPassword = bundle.getString("password");
	        mUrl = bundle.getString("path");
	        mPort = bundle.getInt("port");
	        
	        TextView t = (TextView) findViewById(R.id.path);
	        t.setText(new URL(mUrl).getPath());
	        
	        showProgressDialog();
	        
	        new LsTask().execute("");
        }
        catch (Exception e)
        {
        	e.printStackTrace();
        }
    }
    
    public void onServerResponse()
    {
    	hideProgressDialog();
    	
        adapter = new DirListAdapter(this,ls);
        list = (ListView)findViewById(R.id.list);
        list.setAdapter(adapter);
        
        list.setOnItemClickListener
        (
        	new OnItemClickListener()
        	{
        		@Override
        		public void onItemClick(AdapterView<?> a, View v, int position, long id)
        		{
        			Bundle b = new Bundle();
        			b.putString("host",mHost);
        			b.putString("username",mUsername);
        			b.putString("password",mPassword);
        			b.putString("path", adapter.ls[position].url());
        			b.putInt("port",mPort);
        			
        			if (adapter.ls[position].isDirectory())
        			{	
	        			Intent i = new Intent(DirList.this,DirList.class);
	        			i.putExtras(b);
	        			startActivity(i); 
        			}
        			else
        			{
	        			Intent i = new Intent(DirList.this,ViewFile.class);
	        			i.putExtras(b);
	        			startActivity(i); 
        			}
        		}
        	 }
        );
    }
    
    /**
     * Show the login progress dialog.
     * @return void
     */
    public void showProgressDialog()
    {
    	mProgressDialog = new ProgressDialog(this);
    	mProgressDialog.setCancelable(false);
    	mProgressDialog.setMessage("Loading directory from server.");
		mProgressDialog.show();
    }
    
    /**
     * Hide the login progress dialog.
     * @return void
     */
    public void hideProgressDialog()
    {
    	mProgressDialog.dismiss();
    	mProgressDialog = null;
    }    
    
    @Override
    public void onDestroy()
    {
        list.setAdapter(null);
        super.onDestroy();
    }
}