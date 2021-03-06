#!/usr/bin/python
import tornado.httpserver
import tornado.web
#Use a third-party implementation of digest auth
from curtain import digest

import os
import time
import mimetypes
import base64
import shutil
import urllib

#XML parser library
from xml.dom import minidom



#Helper function to get the text contained in an xml tag
def getText(dom,name):
	try:
		return dom.getElementsByTagName(name)[0].childNodes[0].wholeText
	except:
		raise tornado.web.HTTPError(400,"Invalid XML message (missing tag '%s')" % name ) 


class MainHandler(digest.DigestAuthMixin, tornado.web.RequestHandler):
	#Function used by the digest auth to check credentials
	def getcreds(uname):
		if uname in MainHandler.creds:
			return MainHandler.creds[uname]
		
		
	#Handle Get Request, calling output_file or output_directory to do the actual work
	@digest.digest_auth('Dbox',getcreds)
	def get(self,resource):
		realpath = os.path.realpath(os.path.join(self.WEBROOT, resource))
		#Ensure that the requested path (canonicalized) is actually in the user's home directory 
		userdir =  os.path.realpath(os.path.join(self.WEBROOT, self.params['username']))
		#Check for permission before 404 to avoid leaking the existence of other users' files
		if not realpath.startswith(userdir):
			raise tornado.web.HTTPError(403,"Forbidden")
		if not os.path.exists(realpath):
			raise tornado.web.HTTPError(404)
		if os.path.isdir(realpath):
			self.output_directory(resource,realpath)
		elif os.path.isfile(realpath):
			self.output_file(resource,realpath)
			
				
	#Handle Delete Request (deleting the file or directory tree if allowed)
	@digest.digest_auth('Dbox',getcreds)
	def delete(self,resource):
		realpath = os.path.realpath(os.path.join(self.WEBROOT, resource))
		userdir =  os.path.realpath(os.path.join(self.WEBROOT, self.params['username']))
		if not realpath.startswith(userdir):
			raise tornado.web.HTTPError(403,"Forbidden")
		if not os.path.exists(realpath):
			raise tornado.web.HTTPError(404,"File or directory not found") 
		elif os.path.isdir(realpath):
			if realpath != userdir:
				shutil.rmtree(realpath)
				self.write("Success: Removed the directory")
			else:
				raise tornado.web.HTTPError(403,"Cannot delete home directory")
		elif os.path.isfile(realpath):
			os.remove(realpath)
			self.write("Success: Removed the file")


	#Handle Put Request. Read the xml message from the client; the resource provided in the url is ignored
	@digest.digest_auth('Dbox',getcreds)
	def put(self, resource):
		dom= minidom.parseString(self.request.body)
		resourceName=getText(dom,"ResourceName")
		resourceLocation=getText(dom,"ResourceLocation")
		resourceCategory=dom.getElementsByTagName('Resource')[0].getAttribute('category')

		realpath = os.path.realpath(os.path.join(self.WEBROOT, resourceLocation, resourceName))
		realdirectory = os.path.realpath(os.path.join(self.WEBROOT, resourceLocation)) 
		userdir = os.path.realpath(os.path.join(self.WEBROOT, self.params['username']))
		
		if not realpath.startswith(userdir):
			raise tornado.web.HTTPError(403,"Forbidden")
		if not os.path.isdir(realdirectory):
			raise tornado.web.HTTPError(404,"Directory not found")
		if(resourceCategory == "file"):
			f= open(realpath,"w")
			resourceContent=getText(dom,"ResourceContent")
			resourceEncoding=getText(dom,"ResourceEncoding")
			
			#Decode the base64 if the content was encoded
			if(resourceEncoding == "Base64"):
				resourceContent=base64.b64decode(resourceContent)

			f.write(resourceContent)
			f.close()
		elif(resourceCategory == "directory"):
			if os.path.exists(realpath):
				raise tornado.web.HTTPError(400,"Bad Request: Directory already exists")
			else:
				os.mkdir(realpath)
		else:
			raise tornado.web.HTTPError(400,"Must be a file or a directory")


	#Output a directory entry (resource list)
	def output_directory(self,resource,realpath):
		self.write("<ResourceList>\n")
		entries = os.listdir(realpath)
		#Create an entry for the parent directory, but omit it if the parent is the webroot (don't put a .. entry in /user/)
		if os.path.realpath(os.path.join(realpath,'..')) != os.path.realpath(self.WEBROOT):
			entries.insert(0,'..')

		for e in entries:
			epath = os.path.join(realpath, e)
			stats = os.stat(epath);
			t = time.localtime(stats.st_mtime)
			mime = mimetypes.guess_type(epath)[0]
			#Default if mimetype not detected
			if not mime:
				mime = "application/octet-stream"

			if os.path.isdir(epath):
				category = "directory"
			else:
				category = "file"

			#Write out the xml for this Resource
			self.write("<Resource category=\"%s\">\n" % category)
			self.write("\t<ResourceName>%s</ResourceName>\n" % e)
			self.write("\t<ResourceSize>%i</ResourceSize>\n" % stats.st_size)
			if resource.endswith("/"):
				self.write("\t<ResourceURL>http://%s</ResourceURL>\n" % (self.request.host + "/" + urllib.quote(resource + e)) )
			else:
				self.write("\t<ResourceURL>http://%s</ResourceURL>\n" % (self.request.host + "/" + urllib.quote(resource + "/" + e)))
			self.write("\t<ResourceDate>\n")
			self.write("\t\t<year>%i</year>\n" % t.tm_year)
			self.write("\t\t<month>%i</month>\n" % t.tm_mon)
			self.write("\t\t<day>%i</day>\n" % t.tm_mday)
			self.write("\t\t<hour>%i</hour>\n" % t.tm_hour)
			self.write("\t\t<min>%i</min>\n" % t.tm_min)
			self.write("\t\t<sec>%i</sec>\n" % t.tm_sec)
			self.write("\t</ResourceDate>\n")
			self.write("\t<ResourceType>%s</ResourceType>\n" % mime)
			self.write("</Resource>\n")
		self.write("</ResourceList>")

	#Output a file with its contents
	def output_file(self,resource,realpath):
		self.write("<ResourceDownload>\n")
		stats = os.stat(realpath);
		t = time.localtime(stats.st_mtime)
		mime = mimetypes.guess_type(realpath)[0]
		if not mime:
			mime = "application/octet-stream"
		if mime == "text/plain":
			encoding = "Text"
			use_base64 = False
		else:
			encoding = "Base64"
			use_base64 = True

		self.write("<Resource category=\"file\">\n")
		self.write("\t<ResourceName>%s</ResourceName>\n" % resource.split('/')[-1])
		self.write("\t<ResourceSize>%i</ResourceSize>\n" % stats.st_size)
		#TIME
		self.write("\t<ResourceDate>\n")
		self.write("\t\t<year>%i</year>\n" % t.tm_year)
		self.write("\t\t<month>%i</month>\n" % t.tm_mon)
		self.write("\t\t<day>%i</day>\n" % t.tm_mday)
		self.write("\t\t<hour>%i</hour>\n" % t.tm_hour)
		self.write("\t\t<min>%i</min>\n" % t.tm_min)
		self.write("\t\t<sec>%i</sec>\n" % t.tm_sec)
		self.write("\t</ResourceDate>\n")
		#END TIME
		self.write("\t<ResourceType>%s</ResourceType>\n" % mime)
		self.write("\t<ResourceEncoding>%s</ResourceEncoding>\n" % encoding)
		self.write("\t<ResourceContent>")
		datafile = file(realpath)
		#Read in 3072-byte blocks (which can be encoded to base64 without padding), instead of slurping in the whole file to memory
		data = datafile.read(3072)
		while data:
			if use_base64:
				self.write( base64.b64encode(data) )
			else:
				self.write( data )
			data = datafile.read(3072)
		self.write("</ResourceContent>\n")
		self.write("</Resource>\n")
		self.write("</ResourceDownload>\n")
	

#Handle the PUT /register action
class RegistrationHandler(tornado.web.RequestHandler):
	def put(self):
		dom= minidom.parseString(self.request.body)
		username=getText(dom,"Username").strip()
		password=getText(dom,"Password").strip()
		#Don't allow registration if user already exists (or is a reserved path)
		if (username in MainHandler.creds) or (username in ['register','password']):
			raise tornado.web.HTTPError(400,"User already exists")
		userdir = os.path.realpath(os.path.join(MainHandler.WEBROOT, username))
		if not os.path.exists(userdir):
			os.mkdir(userdir)
		#Add the user to the credentials dictionary and save to the .passwd file for future runs
		MainHandler.creds[username] = {'auth_username': username, 'auth_password': password}
		pwfile=os.path.join(MainHandler.WEBROOT,".passwd")
		f = open(pwfile,"a")
		f.write("%s:%s\n" % (username,password))
		f.close()
		self.write("<Response>User Created</Response>")

#Handle the PUT /password request (to change a user's password)
class PasswordHandler(digest.DigestAuthMixin, tornado.web.RequestHandler):
	def getcreds(uname):
		if uname in MainHandler.creds:
			return MainHandler.creds[uname]

	@digest.digest_auth('Dbox',getcreds)
	def put(self):
		username = self.params['username']
	 	dom= minidom.parseString(self.request.body)
		password=getText(dom,"Password").strip()
		MainHandler.creds[username]['auth_password']= password
		pwfile=os.path.join(MainHandler.WEBROOT,".passwd")
		#Easier to write replace entire password file than to edit the line with the user
		f = open(pwfile,"w")
		for k in MainHandler.creds:
			v = MainHandler.creds[k]
			f.write("%s:%s\n" % (v['auth_username'],v['auth_password']) )
		f.close()
		self.write("<Response>Password Changed</Response>")



#Read the password file and store in the MainHandler class.
#Each line of the password file has the format user:password
def read_passwordfile():
	filename=os.path.join(MainHandler.WEBROOT,".passwd")
	creds = {}
	#If password file doesn't exist, create it
	if not os.path.isfile(filename):
		f=open(filename,'w')
		f.close()
	f = open(filename)
	for l in f:
		if l.find(':') >= 0:
			[user,pw] = l.strip().split(":",1)
			creds[user] = {'auth_username': user, 'auth_password': pw}
	if not creds:
		print "Warning: Password file contained no users. Clients must send register requests before anyone can log in." 
	MainHandler.creds = creds
	MainHandler.pwpath = os.path.realpath(filename)
