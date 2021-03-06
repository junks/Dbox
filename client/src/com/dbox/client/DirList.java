package com.dbox.client;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Displays a directory listing from the server.
 */

public class DirList extends Activity
{
	public static boolean refreshOnResume = false;
	public static boolean finishUnlessHome = false;
	
    private ListView list;
    private DirListAdapter adapter;
    private String mUrl;
    private String mUrlBackup;
    private String mUsername;
    private String mPassword;
    private int mPort;
    private Resource[] ls;
    private ProgressDialog mProgressDialog;
    private boolean deletedChildResource = false;
    private URL mUrlObj;
    
	private class LsTask extends AsyncTask<String, Integer, Integer>
	{
		/**
		 * Executes task on a background thread.
		 */
		@Override
		protected Integer doInBackground(String... data)
		{
			try
			{
				ls = WebService.get(mUrl,mPort,mUsername,mPassword);
			}
			catch (HttpException e)
			{
				return -2;
			}
			catch (Exception e)
			{
				return -1;
			}
			return 1;
		}

		/**
		 * Called on the UI thread after doInBackground has finished.
		 */
		@Override
	    protected void onPostExecute(Integer result)
	    {
			if ( result == 1 )
			{
				onServerResponse();
			}
			else
			{
				if (result == -2)
					printMessage(R.string.error_connect);
				else
					printMessage(R.string.error_login);
				
				openLoginScreen();
			}
	    }
	}
	
	private class MakeDirTask extends AsyncTask<String, Integer, Integer>
	{
		/**
		 * Executes task on a background thread.
		 */
		@Override
		protected Integer doInBackground(String... data)
		{
			try
			{
				WebService.put(mUrl,mPort,mUsername,mPassword,data[0]);
			}
			catch (HttpException e)
			{
				return -2;
			}
			catch (Exception e)
			{
				return -1;
			}
			return 1;
		}

		/**
		 * Called on the UI thread after doInBackground has finished.
		 */
		@Override
	    protected void onPostExecute(Integer result)
	    {
			hideProgressDialog();
			ls();
	    }
	}
	
	private class DeleteTask extends AsyncTask<String, Integer, Integer>
	{
		/**
		 * Executes task on a background thread.
		 */
		@Override
		protected Integer doInBackground(String... data)
		{
			try
			{
				WebService.delete(mUrl,mPort,mUsername,mPassword);
			}
			catch (HttpException e)
			{
				return -2;
			}
			catch (Exception e)
			{
				return -1;
			}
			return 1;
		}

		/**
		 * Called on the UI thread after doInBackground has finished.
		 */
		@Override
	    protected void onPostExecute(Integer result)
	    {
			if ( result == 1 )
			{
				onDeleteResponse();
			}
			else
			{
				if (result == -2)
					printMessage(R.string.error_connect);
				else
					printMessage(R.string.error_login);
				
				openLoginScreen();
			}
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
	        mUsername = bundle.getString("username");
	        mPassword = bundle.getString("password");
	        mUrl = bundle.getString("path");
	        mPort = bundle.getInt("port");
	        
	        mUrlObj = new URL(mUrl);
	        
	        TextView t = (TextView) findViewById(R.id.path);
	        t.setText(mUrlObj.getPath());
	        
	        ls();
        }
        catch (Exception e)
        {
        	e.printStackTrace();
        }
    }
    
    public void ls()
    {
    	if (DirList.refreshOnResume)
    		DirList.refreshOnResume = false;
    	
    	showProgressDialog("Loading directory from server.");
        new LsTask().execute("");
    }
    
    public void delete()
    {
    	showProgressDialog("Removing resource from server.");
    	new DeleteTask().execute("");
    }
    
    public void onDeleteResponse()
    {
    	if (deletedChildResource)
    	{
    		deletedChildResource = false;
    		mUrl = mUrlBackup;
    		hideProgressDialog();
    		ls();
    	}
    	else
    	{
    		DirList.refreshOnResume = true;
    		finish();
    	}
    }
    
    public void onServerResponse()
    {
    	Arrays.sort(ls);
    	
    	hideProgressDialog();
    	
    	if (ls.length==0)
    	{
    		TextView t = (TextView) findViewById(R.id.empty);
    		t.setVisibility(View.VISIBLE);
    	}
    	
        adapter = new DirListAdapter(this,ls);
        list = (ListView)findViewById(R.id.list);
        list.setAdapter(adapter);
        
        registerForContextMenu(list);
        
        list.setOnItemClickListener
        (
        	new OnItemClickListener()
        	{
        		@Override
        		public void onItemClick(AdapterView<?> a, View v, int position, long id)
        		{
        			cd(position);
        		}
        	 }
        );
    }
    
    /**
     * Show the login progress dialog.
     * @return void
     */
    public void showProgressDialog(String message)
    {
    	mProgressDialog = new ProgressDialog(this);
    	mProgressDialog.setCancelable(false);
    	mProgressDialog.setMessage(message);
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
    
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.context_menu, menu);
    }
    
    public boolean onContextItemSelected(MenuItem item)
    {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
		switch (item.getItemId())
		{
			case R.id.view:
				cd((int) info.id);
				return true;
			case R.id.delete:
				mUrlBackup = mUrl;
				mUrl = adapter.ls[(int) info.id].url();
				deletedChildResource = true;
				delete();
				return true;
			default:
				return super.onContextItemSelected(item);
		}
    }
    
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        
        try
        {
        	if (isHomeDir())
        	{
        		menu.setGroupEnabled(R.id.home_group, false);
        		menu.setGroupEnabled(R.id.delete_group, false);
        	}
        }
        catch(Exception e) {}
        
        return true;
    }
    
    
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId())
        {
    	case R.id.refresh:
    		ls();
    		return true;
    	case R.id.delete:
    		delete();
    		return true;
    	case R.id.upload:
    		upload();
    		return true;
    	case R.id.upload_dir:
    		makeDir();
    		return true;
    	case R.id.logout:
    		openLoginScreen();
    		return true;
    	case R.id.home:
    		home();
    		return true;
    	case R.id.password:
    		openChangePasswordScreen();
    		return true;
        }
        return false;
    }
    
    public boolean isHomeDir()
    {
    	return mUrlObj.getPath().equals("/" + mUsername + "/");
    }
    
    public void onWindowFocusChanged(boolean hasFocus)
    {
    	if (finishUnlessHome)
    	{
    		if (isHomeDir())
    		{
    			finishUnlessHome = false;
    		}
    		else
    		{
    			finish();
    		}
    		return;
    	}
    	
    	if (hasFocus && refreshOnResume)
    		ls();
    }
    
    public void cd(int position)
    {
		Bundle b = new Bundle();
		b.putString("username",mUsername);
		b.putString("password",mPassword);
		b.putString("path", adapter.ls[position].url());
		b.putInt("port",mPort);
		
		if (adapter.ls[position].name().equals(".."))
		{
			finish();
		}
		else if (adapter.ls[position].isDirectory())
		{	
			Intent i = new Intent(DirList.this,DirList.class);
			i.putExtras(b);
			startActivity(i); 
		}
		else
		{
			b.putParcelable("resource",adapter.ls[position]);
			Intent i = new Intent(DirList.this,ViewFile.class);
			i.putExtras(b);
			startActivity(i); 
		}
    }
    
    public void openLoginScreen()
    {
		Intent i = new Intent(DirList.this,Login.class);
		i.putExtra("port", mPort);
		i.putExtra("path", mUrl);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		//FLAG_ACTIVITY_CLEAR_TASK
		startActivity(i);
		finish();
    }
    
    public void openChangePasswordScreen()
    {
		Intent i = new Intent(DirList.this,Password.class);
		Bundle b = new Bundle();
		b.putString("username",mUsername);
		b.putString("password",mPassword);
		b.putString("host","http://" + mUrlObj.getHost());
		b.putInt("port", mPort);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		i.putExtras(b);
		startActivity(i);
		finish();
    }
    
    public void printMessage(int string)
    {
    	Toast.makeText(this,string, Toast.LENGTH_LONG).show();
    }
    
    public void home()
    {
    	DirList.finishUnlessHome = true;
    	finish();
    }
    
    public void upload()
    {
    	File uploads = new File(Environment.getExternalStorageDirectory(),"Uploads/");
		Bundle b = new Bundle();
		b.putString("uploadPath", mUrl);
		b.putString("path", uploads.getAbsolutePath());
		b.putString("username",mUsername);
		b.putString("password", mPassword);
		b.putBoolean("isRoot", true);
		b.putInt("port",mPort);
		Intent i = new Intent(this, Upload.class);
		i.putExtras(b);
		startActivity(i);
    }
    
    public void makeDir()
    {
    	final AlertDialog.Builder alert = new AlertDialog.Builder(this);
		final EditText input = new EditText(this);
		alert.setTitle("Folder Name");
		alert.setView(input);
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				String value = input.getText().toString().trim();
				
				StringBuilder sb = new StringBuilder();
				sb.append("<ResourceUpload>");
				sb.append("<Resource category=\"directory\">");
				sb.append("<ResourceName>" + value + "</ResourceName>");
				sb.append("<ResourceLocation>" + mUrlObj.getPath().substring(1) + "</ResourceLocation>");
				sb.append("</Resource>");
				sb.append("</ResourceUpload>");
				
				showProgressDialog("Creating Directory");
				new MakeDirTask().execute(sb.toString());
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.cancel();
					}
				});
		alert.show();
		
		input.requestFocus();
    }
    
    @Override
    public void onDestroy()
    {
    	if (list != null)
    		list.setAdapter(null);
    	
        super.onDestroy();
    }
}