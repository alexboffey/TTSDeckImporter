import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class MakeDeck{
	public static final int CARD_MAINBOARD = 0, CARD_SIDEBOARD = 1, CARD_COMMANDER = 2;
	public static int parseType = CARD_MAINBOARD;

	public static HashMap<String, Card> cardMap = new HashMap<String, Card>();
	public static ArrayList<Card> tokenList = new ArrayList<Card>();
	public static HashMap<String, String> transformMap = new HashMap<String,String>();
	public static String fileName;
	public static String deckName = "default";
	public static String backLink = "default";
	public static String hiddenLink = "default";
	public static ArrayList<String> badCardList = new ArrayList<String>(100);
	public static HashSet<Card> tokenSet = new HashSet<Card>();
	public static String localHostName = "http://www.frogtown.me/";
	public static String tokenFile = "tokens.csv";
	public static String transformFile = "transforms.csv";
	
	public static boolean coolifyBasics;
	public static BufferedImage hiddenCard;
	public static BufferedImage[] buffers;
	public static String[] deckFileNames;
	public static String[] deckLinks;
	public static boolean useImgur;
	
	public static void main(String[] args){
		fileName = args[0];
		
		String hostname = "Unknown";
		try{
		    hostname = InetAddress.getLocalHost().getHostName();
		}catch (UnknownHostException ex){}
		
		//my main server's name is vanilla
		if(!hostname.equalsIgnoreCase("vanilla")){
			localHostName = "http://localhost/";
		}
		
		for(int i = 1; i < args.length; i++){
			useImgur |= args[i].equalsIgnoreCase("imgur")||args[i].equalsIgnoreCase("-imgur");
			
			if(args[i].equalsIgnoreCase("-backURL") && i < args.length-1){
				backLink = args[i+1];
			}

			if(args[i].equalsIgnoreCase("-hiddenURL") && i < args.length-1){
				hiddenLink = args[i+1];
			}
			
			if(args[i].equalsIgnoreCase("-name") && i < args.length-1){
				deckName = args[i+1];
			}
			
			if(args[i].equalsIgnoreCase("-coolifyBasics")){
				coolifyBasics = true;
			}
		}
		
		if(deckName.equals("default")){
			deckName = fileName;
		}
		if(backLink.equals("default")){
			backLink = "http://i.imgur.com/P7qYTcI.png";
		}
		
		loadTokens();
		loadTransforms();
		
		loadCards(fileName);
		downloadImages();
		if(badCardList.size()>0){
			saveBadCards();
			System.exit(1);
		}else{
			stitchDeck();
			if(useImgur){
				postToImgur();
			}
			buildJSONFile();
			System.exit(0);
		}
	}
	
	public static void postToImgur(){
		String namesStr="";
		for(int i = 0; i < deckFileNames.length; i++){
			namesStr += deckFileNames[i]+(i<deckFileNames.length-1?" ":"");
		}
		
		String cmdStr = "nodejs imguruploader.js "+namesStr;
		try{
			Process p = Runtime.getRuntime().exec(cmdStr);
			String[] links = new String[deckFileNames.length];
			p.waitFor();
			
			if(p.exitValue()!=1)throw new Exception();
			
			Scanner pscan = new Scanner(p.getInputStream());
			int curDeck = 0;
			while(pscan.hasNextLine()){
				links[curDeck++] = pscan.nextLine();
			}
			
			if(curDeck != deckLinks.length)throw new Exception();
			
			for(int i = 0; i < deckLinks.length; i++){
				deckLinks[i] = links[i];
			}
			//System.out.println("Uploaded to imgur");
		}catch(Exception e){
			System.out.println("Error getting imgur links");
		}
	}
	
	public static void loadTransforms(){
		File tFile = new File(transformFile);
		try{
			Scanner fscan = new Scanner(tFile);
			while(fscan.hasNextLine()){
				String line = fscan.nextLine().toLowerCase();
				Scanner lscan = new Scanner(line);
				lscan.useDelimiter(",");
				String a=lscan.next().replaceAll(";", ","),b=lscan.next().replaceAll(";", ",");
				transformMap.put(a, b);
				System.out.println("added {" + a+","+b+"}");
			}
		}catch(Exception e){
			System.out.println("Unable to find transform file!");
		}
	}
	
	public static void loadTokens(){
		//load tokens into
		File tFile = new File(tokenFile);
		try{
			Scanner fscan = new Scanner(tFile);
			String lastname = "";
			String lastcolor = "";
			String lasttype = "";
			while(fscan.hasNextLine()){
				String line = fscan.nextLine().replaceAll(",", " ,");
				Scanner lscan = new Scanner(line);
				lscan.useDelimiter(",");
				
				String name = lscan.next().trim();
				if(name.length() > 0){
					lastname = name;
				}
				name = lastname;
				
				String color = lscan.next().trim().replaceAll("/", "").toLowerCase();
				if(color.length() > 0){
					lastcolor = color;
				}
				color = lastcolor;
				
				String type = lscan.next().trim();
				if(type.length() > 0){
					lasttype = type;
				}
				type = lasttype;
				
				String pt = lscan.next().trim();
				
				String card_list = lscan.next().trim().toLowerCase();
				
				Card token = new Card();
				token.token = true;
				token.name = name;
				token.color = color;
				token.pt = pt;
				token.cardlist = "||"+card_list+"||";
				token.imageFileName = (("tokens/"+name+"("+color+")[").toLowerCase()+pt+"].jpg").replaceAll(" ", "");
				tokenList.add(token);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		
		for(Card s : tokenList){
			System.out.println(s.getDisplay());
		}
	}
	
	public static void saveBadCards(){
		String badStr = "[";
		for(int i = 0; i < badCardList.size(); i++){
			badStr += "\""+badCardList.get(i)+(i<badCardList.size()-1?"\", ":"\"");
		}
		badStr+="]";
		
		try{
			PrintWriter fileWriter = new PrintWriter(new File("decks/"+fileName+".json"));
			fileWriter.write(badStr);
			fileWriter.close();
		}catch(Exception e){
			System.out.println("Error saving deck json");
			e.printStackTrace();
		}
	}
	public static String getGUID(){

		String chars = "0123456789abcdef";
		String uid = "";
		for(int i = 0; i < 6; i++){
			uid += chars.charAt((int)Math.floor(Math.random()*chars.length()));
		}
		return uid;
	}
	
	public static void buildJSONFile(){
		
		JsonObject deckJSON = new JsonObject();
		deckJSON.add("SaveName", new JsonPrimitive(""));
		deckJSON.add("GameMode", new JsonPrimitive(""));
		deckJSON.add("Date", new JsonPrimitive(""));
		deckJSON.add("Table", new JsonPrimitive(""));
		deckJSON.add("Sky", new JsonPrimitive(""));
		deckJSON.add("Note", new JsonPrimitive(""));
		deckJSON.add("Rules", new JsonPrimitive(""));
		deckJSON.add("PlayerTurn", new JsonPrimitive(""));
		
		JsonObject stateColor = new JsonObject();
		stateColor.add("r", new JsonPrimitive(0.713235259));
		stateColor.add("g", new JsonPrimitive(0.713235259));
		stateColor.add("b", new JsonPrimitive(0.713235259));
		
		JsonArray objectStates = new JsonArray();
		//commander object state----------------------------------------
		
		JsonObject commanderState = new JsonObject();
		commanderState.add("Name", new JsonPrimitive("Card"));
		commanderState.add("Nickname", new JsonPrimitive(""));
		commanderState.add("Description", new JsonPrimitive(""));
		commanderState.add("Grid", new JsonPrimitive(true));
		commanderState.add("Locked", new JsonPrimitive(false));
		commanderState.add("SidewaysCard", new JsonPrimitive(false));
		commanderState.add("GUID", new JsonPrimitive(getGUID()));
		
		//use same color
		commanderState.add("ColorDiffuse", stateColor);
		
		JsonObject commanderStatePos = new JsonObject();
		commanderStatePos.add("posX", new JsonPrimitive(-2.5));
		commanderStatePos.add("posY", new JsonPrimitive(0));
		commanderStatePos.add("posZ", new JsonPrimitive(0));
		commanderStatePos.add("rotX", new JsonPrimitive(180));
		commanderStatePos.add("rotY", new JsonPrimitive(0));
		commanderStatePos.add("rotZ", new JsonPrimitive(180));
		commanderStatePos.add("scaleX", new JsonPrimitive(1.25));
		commanderStatePos.add("scaleY", new JsonPrimitive(1.25));
		commanderStatePos.add("scaleZ", new JsonPrimitive(1.25));
		commanderState.add("Transform", commanderStatePos);
		
		JsonArray commanderStateIDs = new JsonArray();
		ArrayList<Integer> commanderStateDeckIDs = new ArrayList<Integer>();
		for(String key : cardMap.keySet()){
			Card card = cardMap.get(key);
			int deckID = card.ID/100;
			if(card.commanderAmt > 0){
				if(!commanderStateDeckIDs.contains(deckID)){
					commanderStateDeckIDs.add(deckID);
				}
				commanderStateIDs.add(new JsonPrimitive(card.ID));
				break;
			}
		}
		if(commanderStateIDs.size() == 1){
			commanderState.add("CardID", commanderStateIDs.get(0));
		}else{
			commanderState.add("DeckIDs", commanderStateIDs);
		}
		
		JsonObject commanderStateDecks = new JsonObject();
		for(int i = 0; i < commanderStateDeckIDs.size(); i++){
			int deckID = commanderStateDeckIDs.get(i);
			JsonObject deckObj = new JsonObject();
			deckObj.add("FaceURL", new JsonPrimitive(deckLinks[deckID-1]));
			deckObj.add("BackURL", new JsonPrimitive(backLink));
			commanderStateDecks.add(""+deckID, deckObj);
		}
		commanderState.add("CustomDeck", commanderStateDecks);
		
		if(commanderStateIDs.size()>0)objectStates.add(commanderState);
		//token object state----------------------------------------
		if(tokenSet.size() > 0){
			JsonObject tokenState = new JsonObject();
			tokenState.add("Name", new JsonPrimitive(tokenSet.size()==1?"Card":"DeckCustom"));
			tokenState.add("Nickname", new JsonPrimitive(""));
			tokenState.add("Description", new JsonPrimitive(""));
			tokenState.add("Grid", new JsonPrimitive(true));
			tokenState.add("Locked", new JsonPrimitive(false));
			tokenState.add("SidewaysCard", new JsonPrimitive(false));
			tokenState.add("GUID", new JsonPrimitive(getGUID()));
			
			//use same color
			tokenState.add("ColorDiffuse", stateColor);
			
			JsonObject tokenStatePos = new JsonObject();
			tokenStatePos.add("posX", new JsonPrimitive(2.5));
			tokenStatePos.add("posY", new JsonPrimitive(2.5));
			tokenStatePos.add("posZ", new JsonPrimitive(0));
			tokenStatePos.add("rotX", new JsonPrimitive(180));
			tokenStatePos.add("rotY", new JsonPrimitive(0));
			tokenStatePos.add("rotZ", new JsonPrimitive(180));
			tokenStatePos.add("scaleX", new JsonPrimitive(1.0));
			tokenStatePos.add("scaleY", new JsonPrimitive(1.0));
			tokenStatePos.add("scaleZ", new JsonPrimitive(1.0));
			tokenState.add("Transform", tokenStatePos);
			
			JsonArray tokenStateIDs = new JsonArray();
			ArrayList<Integer> tokenStateDeckIDs = new ArrayList<Integer>();
			for(Card card : tokenSet){
				int deckID = card.ID/100;
				if(!tokenStateDeckIDs.contains(deckID)){
					tokenStateDeckIDs.add(deckID);
				}
				tokenStateIDs.add(new JsonPrimitive(card.ID));
				System.out.println("Added token " + card.ID);
			}
			if(tokenStateIDs.size() == 1){
				tokenState.add("CardID", tokenStateIDs.get(0));
			}else{
				tokenState.add("DeckIDs", tokenStateIDs);
			}
			
			JsonObject tokenStateDecks = new JsonObject();
			for(int i = 0; i < tokenStateDeckIDs.size(); i++){
				int deckID = tokenStateDeckIDs.get(i);
				JsonObject deckObj = new JsonObject();
				deckObj.add("FaceURL", new JsonPrimitive(deckLinks[deckID-1]));
				deckObj.add("BackURL", new JsonPrimitive(backLink));
				tokenStateDecks.add(""+deckID, deckObj);
			}
			tokenState.add("CustomDeck", tokenStateDecks);
			
			if(tokenStateDeckIDs.size()>0){
				objectStates.add(tokenState);
				System.out.println("Added token state?");
			}else{
				System.out.println("didnt added token state?");
			}
		}
		//main deck object state---------------------------------------------
		JsonObject mainState = new JsonObject();
		mainState.add("Name", new JsonPrimitive("DeckCustom"));
		mainState.add("Nickname", new JsonPrimitive(""));
		mainState.add("Description", new JsonPrimitive(""));
		mainState.add("Grid", new JsonPrimitive(true));
		mainState.add("Locked", new JsonPrimitive(false));
		mainState.add("SidewaysCard", new JsonPrimitive(false));
		mainState.add("GUID", new JsonPrimitive(getGUID()));
		
		mainState.add("ColorDiffuse", stateColor);
		
		JsonObject mainStatePos = new JsonObject();
		mainStatePos.add("posX", new JsonPrimitive(0));
		mainStatePos.add("posY", new JsonPrimitive(1));
		mainStatePos.add("posZ", new JsonPrimitive(0));
		mainStatePos.add("rotX", new JsonPrimitive(0));
		mainStatePos.add("rotY", new JsonPrimitive(180));
		mainStatePos.add("rotZ", new JsonPrimitive(180));
		mainStatePos.add("scaleX", new JsonPrimitive(1));
		mainStatePos.add("scaleY", new JsonPrimitive(1));
		mainStatePos.add("scaleZ", new JsonPrimitive(1));
		mainState.add("Transform", mainStatePos);
		
		JsonArray mainStateIDs = new JsonArray();
		ArrayList<Integer> mainStateDeckIDs = new ArrayList<Integer>();
		for(String key : cardMap.keySet()){
			Card card = cardMap.get(key);
			int deckID = card.ID/100;
			if(!mainStateDeckIDs.contains(deckID)){
				mainStateDeckIDs.add(deckID);
			}
			for(int i = 0; i < card.mainAmt; i++){
				mainStateIDs.add(new JsonPrimitive(card.ID));
			}
		}
		mainState.add("DeckIDs", mainStateIDs);
		
		JsonObject mainStateDecks = new JsonObject();
		for(int i = 0; i < mainStateDeckIDs.size(); i++){
			int deckID = mainStateDeckIDs.get(i);
			JsonObject deckObj = new JsonObject();
			deckObj.add("FaceURL", new JsonPrimitive(deckLinks[deckID-1]));
			deckObj.add("BackURL", new JsonPrimitive(backLink));
			mainStateDecks.add(""+deckID, deckObj);
		}
		mainState.add("CustomDeck", mainStateDecks);
		
		if(mainStateIDs.size()>0)objectStates.add(mainState);
		//side board state ---------------------------------------------------

		JsonObject sideState = new JsonObject();
		sideState.add("Name", new JsonPrimitive("DeckCustom"));
		sideState.add("Nickname", new JsonPrimitive(""));
		sideState.add("Description", new JsonPrimitive(""));
		sideState.add("Grid", new JsonPrimitive(true));
		sideState.add("Locked", new JsonPrimitive(false));
		sideState.add("SidewaysCard", new JsonPrimitive(false));
		sideState.add("GUID", new JsonPrimitive(getGUID()));
		
		//use same color
		sideState.add("ColorDiffuse", stateColor);
		
		JsonObject sideStatePos = new JsonObject();
		sideStatePos.add("posX", new JsonPrimitive(2.5));
		sideStatePos.add("posY", new JsonPrimitive(0));
		sideStatePos.add("posZ", new JsonPrimitive(0));
		sideStatePos.add("rotX", new JsonPrimitive(0));
		sideStatePos.add("rotY", new JsonPrimitive(180));
		sideStatePos.add("rotZ", new JsonPrimitive(180));
		sideStatePos.add("scaleX", new JsonPrimitive(1));
		sideStatePos.add("scaleY", new JsonPrimitive(1));
		sideStatePos.add("scaleZ", new JsonPrimitive(1));
		sideState.add("Transform", sideStatePos);
		
		JsonArray sideStateIDs = new JsonArray();
		ArrayList<Integer> sideStateDeckIDs = new ArrayList<Integer>();
		for(String key : cardMap.keySet()){
			Card card = cardMap.get(key);
			int deckID = card.ID/100;
			if(!sideStateDeckIDs.contains(deckID)){
				sideStateDeckIDs.add(deckID);
			}
			for(int i = 0; i < card.sideAmt; i++){
				sideStateIDs.add(new JsonPrimitive(card.ID));
			}
		}
		sideState.add("DeckIDs", sideStateIDs);
		
		JsonObject sideStateDecks = new JsonObject();
		for(int i = 0; i < sideStateDeckIDs.size(); i++){
			int deckID = sideStateDeckIDs.get(i);
			JsonObject deckObj = new JsonObject();
			deckObj.add("FaceURL", new JsonPrimitive(deckLinks[deckID-1]));
			deckObj.add("BackURL", new JsonPrimitive(backLink));
			System.out.println("back: " + backLink);
			sideStateDecks.add(""+deckID, deckObj);
		}
		sideState.add("CustomDeck", sideStateDecks);
		
		if(sideStateIDs.size()>0)objectStates.add(sideState);
		///////////////////////////////////////////////////////// end side
		
		deckJSON.add("ObjectStates", objectStates);
		
		String deckStr = new GsonBuilder().setPrettyPrinting().create().toJson(deckJSON);
		
		try{
			PrintWriter fileWriter = new PrintWriter(new File("decks/"+fileName+".json"));
			fileWriter.write(deckStr);
			fileWriter.close();
		}catch(Exception e){
			System.out.println("Error saving deck json");
			e.printStackTrace();
		}
	}
	
	public static void stitchDeck(){
		int deckAmt = (int)Math.ceil((tokenSet.size() + cardMap.keySet().size())/69.0);
		
		int cardOffsetX = 10;
		int cardOffsetY = 10;
		
		int cardWidth = 312 + 2*cardOffsetX;
		int cardHeight = 445 + 2*cardOffsetY;
		
		int cardsPerDeck = 69;
		
		buffers = new BufferedImage[deckAmt];
		deckFileNames = new String[deckAmt];
		deckLinks = new String[deckAmt];
		
		for(int i = 0; i < deckAmt; i++){
			deckFileNames[i] = "decks/"+fileName+i+".jpg";
			buffers[i] = new BufferedImage(cardWidth * 10, cardHeight * 7, BufferedImage.TYPE_3BYTE_BGR);
			Graphics g = buffers[i].getGraphics();
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, cardWidth * 10, cardHeight * 7);
			if(hiddenCard != null){
				g.drawImage(hiddenCard, cardWidth * 9, cardHeight * 6, cardWidth, cardHeight, null);
			}
		}
		int cardCount = 0;
		for(String key : cardMap.keySet()){
			Card card = cardMap.get(key);
			
			int deck = cardCount / cardsPerDeck;
			int deckID = cardCount % cardsPerDeck;
			card.ID = 100*(1+deck) + deckID;
			System.out.println("Stitching " +card.ID+ card.getDisplay());
			
			int gridX = deckID%10;
			int gridY = deckID/10;

			int realX = gridX * cardWidth + cardOffsetX;
			int realY = gridY * cardHeight + cardOffsetY;
			try{
				BufferedImage cardImage = ImageIO.read(new File(card.imageFileName));
				buffers[deck].getGraphics().drawImage(cardImage, realX, realY, cardWidth - cardOffsetX*2, cardHeight - cardOffsetY*2, null);
			}catch(Exception e){
				e.printStackTrace();
			}
			cardCount++;
		}
		for(Card token : tokenSet){
			
			int deck = cardCount / cardsPerDeck;
			int deckID = cardCount % cardsPerDeck;
			token.ID = 100*(1+deck) + deckID;
			
			System.out.println("Stitching " +token.ID+ token.getDisplay());
			
			int gridX = deckID%10;
			int gridY = deckID/10;

			int realX = gridX * cardWidth + cardOffsetX;
			int realY = gridY * cardHeight + cardOffsetY;
			try{
				BufferedImage cardImage = ImageIO.read(new File(token.imageFileName));
				buffers[deck].getGraphics().drawImage(cardImage, realX, realY, cardWidth - cardOffsetX*2, cardHeight - cardOffsetY*2, null);
			}catch(Exception e){
				System.out.println("Couldn't add " + token.imageFileName + " to deck");
				e.printStackTrace();
			}
			cardCount++;
		}
		
		for(int i = 0; i < deckAmt; i++){
			try{
				ImageIO.write(buffers[i], "jpg", new File(deckFileNames[i]));
				deckLinks[i] = localHostName+deckFileNames[i];
			}catch(Exception e){
				System.out.println("Error writing deck to file");
				e.printStackTrace();
			}
		}
	}
	
	public static String getHTML(String urlToRead) {
	      URL url;
	      HttpURLConnection conn;
	      BufferedReader rd;
	      String line;
	      String result = "";
	      try {
	         url = new URL(urlToRead);
	         conn = (HttpURLConnection) url.openConnection();
	         conn.setRequestMethod("GET");
	         rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
	         while ((line = rd.readLine()) != null) {
	            result += line;
	         }
	         rd.close();
	      } catch (IOException e) {
	         e.printStackTrace();
	      } catch (Exception e) {
	         e.printStackTrace();
	      }
	      return result;
	   }
	
	public static void downloadImages(){
		for(String key : cardMap.keySet()){
			Card card = cardMap.get(key);
			
			URI uri = null;
			String qstr = null;
			try{
				String processedName = card.name.trim().toLowerCase();
				
				if(card.set != null && card.set.length() > 0) processedName +=" e:"+card.set;
				if(card.lang != null && card.lang.length() > 0)	processedName +=" l:"+card.lang;
				
				uri = new java.net.URI("http", "magiccards.info", "/query", "q="+processedName+"&v=card&s=cname", null);
				qstr = uri.toURL().toString();
			}catch(Exception e){e.printStackTrace();}
			
			String imageFileName = "images/"+card.cardKey.toLowerCase().replaceAll("/", ".")+".jpg";
			
			card.imageFileName = imageFileName;
			File f = new File(card.imageFileName);
			if(!f.exists()){
				String result = getHTML(qstr);
				String regexStr = "(http:\\/\\/magiccards.info\\/[a-z0-9/]+\\.jpg)\"\\s+alt=\\\""+(card.name.replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)"))+"\"";
				regexStr = regexStr.replaceAll("(?i)ae", "(((?i)ae)|(Æ))");
				Pattern regex = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE);
				Matcher matcher = regex.matcher(result);
				matcher.find();
				if(matcher.groupCount() > 0){
					try {
						saveImage(matcher.group(1), card.imageFileName);
					} catch (Exception e) {
						badCardList.add(card.getDisplay());
					}
				}else{
					badCardList.add(card.getDisplay());
				}
			}
		}
		
		for(Card card : tokenSet){
			if(!card.transform)continue;
			
			URI uri = null;
			String qstr = null;
			try{
				String processedName = card.name.trim().toLowerCase();
				uri = new java.net.URI("http", "magiccards.info", "/query", "q="+processedName+"&v=card&s=cname", null);
				qstr = uri.toURL().toString();
			}catch(Exception e){e.printStackTrace();}
			
			String imageFileName = "images/"+card.cardKey.toLowerCase().replaceAll("/", ".")+".jpg";
			
			card.imageFileName = imageFileName;
			File f = new File(card.imageFileName);
			if(!f.exists()){
				String result = getHTML(qstr);
				String regexStr = "(http:\\/\\/magiccards.info\\/[a-z0-9/]+\\.jpg)\"\\s+alt=\\\""+(card.name.replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)"))+"\"";
				regexStr = regexStr.replaceAll("(?i)ae", "(((?i)ae)|(Æ))");
				Pattern regex = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE);
				Matcher matcher = regex.matcher(result);
				matcher.find();
				if(matcher.groupCount() > 0){
					try {
						saveImage(matcher.group(1), card.imageFileName);
					} catch (Exception e) {
						badCardList.add(card.getDisplay());
					}
				}else{
					badCardList.add(card.getDisplay());
				}
			}
		}
	
		if(!hiddenLink.equals("default")){
			try{
				URL url = new URL(hiddenLink);
				HttpURLConnection httpcon = (HttpURLConnection) url.openConnection(); 
				httpcon.addRequestProperty("User-Agent", "Mozilla/4.76"); 
				hiddenCard = ImageIO.read(httpcon.getInputStream());
			}catch(Exception e){
				e.printStackTrace();
				hiddenLink = "default";
				hiddenCard = null;
			}
		}
	}
	
	public static String parseSet(String line){
		String set = "";
		
		if(line.indexOf("[") > 0 && line.indexOf("]") > 0 && line.indexOf("[") < line.indexOf("]")){
			set = line.substring(line.indexOf("[")+1, line.indexOf("]"));
		}
		
		return set;
	}
	
	public static String parseLang(String line){
		String lang = "";
		
		if(line.indexOf("{") > 0 && line.indexOf("}") > 0 && line.indexOf("{") < line.indexOf("}")){
			lang = line.substring(line.indexOf("{")+1, line.indexOf("}"));
		}
		
		return lang;
	}
	
	public static void loadCards(String fileName){
		try{
			File f = new File("lists/"+fileName);
			Scanner fscan = new Scanner(new FileInputStream(f));
			Pattern cardNameRegex = Pattern.compile("([0-9]*)x?\\s*(.*)");
			while(fscan.hasNextLine()){
				String line = fscan.nextLine().trim();
				if(line.length() == 0)continue;
				if(line.toUpperCase().endsWith(" CREATURES"))continue;
				if(line.toUpperCase().endsWith(" INSTANTS AND SORC."))continue;
				if(line.toUpperCase().endsWith(" LANDS"))continue;
				if(line.toUpperCase().endsWith(" OTHER SPELLS"))continue;
				if(line.toUpperCase().startsWith("CREATURE ("))continue;
				if(line.toUpperCase().startsWith("INSTANT ("))continue;
				if(line.toUpperCase().startsWith("LAND ("))continue;
				if(line.toUpperCase().startsWith("PLANESWALKER ("))continue;
				if(line.toUpperCase().startsWith("TCG $"))continue;
				if(line.toUpperCase().startsWith("SIDEBOARD ("))continue;
				if(line.toUpperCase().startsWith("ENCHANTMENT ("))continue;
				if(line.toUpperCase().startsWith("SORCERY ("))continue;
				if(line.toUpperCase().startsWith("MAYBEBOARD ("))continue;
				
				if(line.equalsIgnoreCase("MAINBOARD")){
					parseType = CARD_MAINBOARD;
				}else if(line.equalsIgnoreCase("SIDEBOARD")){
					parseType = CARD_SIDEBOARD;
				}else if(line.equalsIgnoreCase("COMMANDER")){
					parseType = CARD_COMMANDER;
				}else{
					Matcher matcher = cardNameRegex.matcher(line);
					matcher.find();
					int amt = Integer.parseInt("0"+matcher.group(1));
					if(amt == 0)amt = 1;
					String name = matcher.group(2).toLowerCase();
					if(name.indexOf("[")>0){
						name = name.substring(0, name.indexOf("[")).trim();
					}
					if(name.indexOf("{")>0){
						name = name.substring(0, name.indexOf("{")).trim();
					}
					
					String set = parseSet(line);
					String lang = parseLang(line);
					
					String cardKey = name+"["+set+"]{"+lang+"}";
					if(name.contains("/")){
						//Wear // Tear
						String leftHalf = name.substring(0, name.indexOf("/")).trim();
						String rightHalf = name.substring(name.lastIndexOf("/")+1).trim();
						
						name = leftHalf+=" ("+leftHalf+"/"+rightHalf+")";
					}
					if(cardMap.containsKey(cardKey)){
						if(parseType == CARD_MAINBOARD) cardMap.get(cardKey).mainAmt += amt;
						if(parseType == CARD_SIDEBOARD) cardMap.get(cardKey).sideAmt += amt;
						if(parseType == CARD_COMMANDER) cardMap.get(cardKey).commanderAmt += amt;
					}else{
						Card card = new Card();
						card.name = name;
						card.cardKey = cardKey;
						card.set = set;
						card.lang = lang;
						if(parseType == CARD_MAINBOARD) card.mainAmt = amt;
						if(parseType == CARD_SIDEBOARD) card.sideAmt = amt;
						if(parseType == CARD_COMMANDER) card.commanderAmt = amt;
						cardMap.put(cardKey, card);
						
						if(transformMap.containsKey(card.name)){
							Card transformCard = new Card();
							transformCard.transform = true;
							transformCard.name = transformMap.get(card.name);
							transformCard.cardKey = transformCard.name;
							tokenSet.add(transformCard);
							System.out.println("added " + transformCard);
						}
						System.out.println("added " + card);
					}
				}
			}
		}catch(Exception e){
			System.out.println("Error loading cards");
			e.printStackTrace();
		}
		
		if(coolifyBasics){
			String[] basics = {"island","forest","mountain","swamp","plains"};
			for(String basic : basics){
				if(cardMap.containsKey(basic+"[]{}")){
					Card card = cardMap.remove(basic+"[]{}");
					System.out.println(card);
					String[] coolSets = {"uh","guru","al","zen"};
					int[] setCounts = new int[coolSets.length];
					int[] setSideCounts = new int[coolSets.length];
					int[] setCommandCounts = new int[coolSets.length];
					for(int i = 0; i < card.mainAmt; i++){
						int set_i = (int)(Math.random()*coolSets.length);
						setCounts[set_i]++;
					}
					for(int i = 0; i < card.sideAmt; i++){
						int set_i = (int)(Math.random()*coolSets.length);
						setSideCounts[set_i]++;
					}
					for(int i = 0; i < card.commanderAmt; i++){
						int set_i = (int)(Math.random()*coolSets.length);
						setCommandCounts[set_i]++;
					}
					card.mainAmt = 0;
					card.sideAmt = 0;
					card.commanderAmt = 0;
					
					for(int i = 0; i < coolSets.length; i++){
						String cardKey = basic+"["+coolSets[i]+"]{}";
						
						if(cardMap.containsKey(cardKey)){
							cardMap.get(cardKey).mainAmt += setCounts[i];
							cardMap.get(cardKey).sideAmt += setSideCounts[i];
							cardMap.get(cardKey).commanderAmt += setCommandCounts[i];
						}else{
							Card newCard = new Card();
							newCard.name = basic;
							newCard.cardKey = cardKey;
							newCard.set = coolSets[i];
							newCard.lang = "";
							newCard.mainAmt = setCounts[i];
							newCard.sideAmt = setSideCounts[i];
							newCard.commanderAmt = setCommandCounts[i];
							if(newCard.mainAmt > 0 || newCard.sideAmt > 0 || newCard.commanderAmt > 0){
								cardMap.put(cardKey, newCard);
								System.out.println("added " + newCard);
							}
						}
					}
				}
			}
		}
		
		for(String s : cardMap.keySet()){
			Card card = cardMap.get(s);
			for(Card token : tokenList){
				if(token.cardlist.toLowerCase().contains("||"+card.name.replaceAll(",", ";").toLowerCase()+"||")){
					tokenSet.add(token);
				}
			}
		}
	}
	
	public static void saveImage(String imageUrl, String destinationFile) throws IOException {
		URL url = new URL(imageUrl);
		InputStream is = url.openStream();
		OutputStream os = new FileOutputStream(destinationFile);

		byte[] b = new byte[2048];
		int length;

		while ((length = is.read(b)) != -1) {
			os.write(b, 0, length);
		}

		is.close();
		os.close();
	}
	static class Card implements Comparable<Card>{
		String name;
		String imageFileName;
		String cardKey;
		String set;
		String lang;
		String color;
		String pt;
		String cardlist;
		boolean token;
		boolean transform;
		int ID;
		int commanderAmt;
		int mainAmt;
		int sideAmt;
		public String getDisplay(){
			String s = name;
			if(token){
				s+="("+color+")["+pt+"]";
			}else{
				if(set != null && set.length() > 0)
					s+="["+set+"]";
				if(lang != null && lang.length() > 0)
					s+="{"+lang+"}";
			}
			return s;
		}
		public String toString(){
			return "{"+name+":"+mainAmt+", key:"+cardKey+"}";
		}
		@Override
		public int compareTo(Card c) {
			if(!token)
				return getDisplay().compareTo(c.getDisplay());
			return (name+color+pt+cardlist).compareTo(c.name+c.color+c.pt+c.cardlist);
		}
	}
}