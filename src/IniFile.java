import net.spy.memcached.MemcachedClient;
import java.io.*;
import java.util.*;



/**
 * A class for handling Windows-style INI files. The file format is as 
 * follows:  <dl>
 *<dd>   [subject]       - anything beginning with [ and ending with ] is a subject 
 *<dd>   ;comment        - anything beginning with a ; is a comment 
 *<dd>   variable=value  - anything of the format string=string is an assignment 
 *<dd>   comment         - anything that doesn't match any of the above is a comment 
 * </dl>
 * @author Steve DeGroof
 * @author degroof@mindspring.com
 * 
 * @author James Jarvis (added isTrue()) 
 */
public class IniFile extends Object
{
	/**Actual text lines of the file stored in a vector.*/
	protected Vector<String> lines;       
	/**A vector of all subjects*/
	protected Vector<String> subjects;    
	/**A vector of variable name vectors grouped by subject*/


	protected Vector<Vector> variables;   
	/**A vector of variable value vectors grouped by subject*/

	protected Vector<Vector> values;      
	/**Name of the file*/
	protected String fileName;   
	/**If true, INI file will be saved every time a value is changed. Defaults to false*/
	protected boolean saveOnChange = false;

	protected MemcachedClient memcache = null;
	protected int memcacheTimeout = 3600*24*2;

	/**
	 * Creates an INI file object using the specified name
	 * If the named file doesn't exist, create one
	 * @param name the name of the file
	 */
	public IniFile(String name)  
	{
		this(name,false);
	}

	/**
	 * Creates an INI file object using the specified name
	 * If the named file doesn't exist, create one
	 * @param name the name of the file
	 * @param saveOnSet save file whenever a value is set
	 */
	public IniFile(String name, boolean save)  
	{
		saveOnChange = save;
		fileName = name;
		if (!((new File(name)).exists()))
		{
			if (!createFile()) return;
		}
		loadFile();
		parseLines();
	}


	public void setMemcache(MemcachedClient m, int timeout) {
		memcache=m;
		if ( timeout > 0)
			memcacheTimeout=timeout;
	}


	/**
	 * Loads and parses the INI file. Can be used to reload from file.
	 */

	@SuppressWarnings("deprecation")
	public void loadFile()
	{

		//reset all vectors
		lines = new Vector<String>();     
		subjects = new Vector<String>();  
		variables = new Vector<Vector>(); 
		values = new Vector<Vector>();    
		//open the file
		try 
		{
			DataInputStream ini = new DataInputStream(
					new BufferedInputStream(
							new FileInputStream(fileName)));
			String line = "";
			//read all the lines in
			while (true)
			{
				line = ini.readLine();

				if (line == null)
					break;
				else
					lines.addElement(line.trim());
			}
			ini.close();
		}
		catch (IOException e)
		{
			System.out.println("IniFile load failed: " + e.getMessage());
			e.printStackTrace();
		}
	}


	/**
	 * Create a new INI file.
	 */
	protected boolean createFile()
	{
		try 
		{
			DataOutputStream newFile = new DataOutputStream(new FileOutputStream(fileName));
			newFile.writeBytes(";INI File: "+fileName+System.getProperty("line.separator"));
			newFile.close();
			return true;
		}
		catch (IOException e)
		{
			System.out.println("IniFile create failed: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}


	/**
	 * Reads lines, filling in subjects, variables and values.
	 */
	protected void parseLines()
	{
		String currentLine = null;    //current line being parsed
		String currentSubject = null; //the last subject found
		for (int i=0;i<lines.size();i++) //parse all lines
		{
			currentLine = lines.elementAt(i); 
			if (isaSubject(currentLine)) //if line is a subject, set currentSubject
			{
				currentSubject = currentLine.substring(1,currentLine.length()-1);
			}
			else if (isanAssignment(currentLine)) //if line is an assignment, add it
			{
				String assignment = currentLine;
				addAssignment(currentSubject,assignment);
			}
		}
	}


	/**
	 * Adds and assignment (i.e. "variable=value") to a subject.
	 */
	protected boolean addAssignment(String subject, String assignment)
	{
		String value;
		String variable;
		int index = assignment.indexOf("=");
		variable = assignment.substring(0,index);
		value = assignment.substring(index+1,assignment.length());
		if ((value.length()==0) || (variable.length()==0)) return false;
		else return addValue(subject, variable, value, false);
	}


	/**
	 * Sets a specific subject/variable combination the given value. If the subject
	 * doesn't exist, create it. If the variable doesn't exist, create it. If 
	 * saveOnChange is true, save the file;
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @param variable the variable name (e.g. "Color")
	 * @param value the value of the variable (e.g. "green")
	 * @return true if successful
	 */
	public boolean setValue(String subject, String variable, String value)
	{
		boolean result = addValue(subject, variable, value, true);
		if (saveOnChange) saveFile();
		return result;
	}


	/**
	 * Sets a specific subject/variable combination the given value. If the subject
	 * doesn't exist, create it. If the variable doesn't exist, create it.
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @param variable the variable name (e.g. "Color")
	 * @param value the value of the variable (e.g. "green")
	 * @param addToLines add the information to the lines vector
	 * @return true if successful
	 */
	@SuppressWarnings("unchecked")
	protected boolean addValue(String subject, String variable, String value, boolean addToLines)
	{
		//if no subject, quit
		if ((subject == null) || (subject.length()==0)) return false;

		//if no variable, quit
		if ((variable == null) || (variable.length()==0)) return false;

		//if the subject doesn't exist, add it to the end
		if (!subjects.contains(subject)) 
		{
			subjects.addElement(subject);
			variables.addElement(new Vector());
			values.addElement(new Vector());
		}

		//set the value, if the variable doesn't exist, add it to the end of the subject
		int subjectIndex = subjects.indexOf(subject);
		Vector<String> subjectVariables = (variables.elementAt(subjectIndex));
		Vector<String> subjectValues = (values.elementAt(subjectIndex));
		if (!subjectVariables.contains(variable))
		{
			subjectVariables.addElement(variable);
			subjectValues.addElement(value);
		}
		int variableIndex = subjectVariables.indexOf(variable);
		subjectValues.setElementAt(value,variableIndex);

		//add it to the lines vector?
		if (addToLines)
			setLine(subject,variable,value);

		return true;
	}

	/**
	 * does the line represent a subject?
	 * @param line a string representing a line from an INI file
	 * @return true if line is a subject
	 */
	protected boolean isaSubject(String line)
	{
		return (line.startsWith("[") && line.endsWith("]"));
	}

	/**
	 * set a line in the lines vector 
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @param variable the variable name (e.g. "Color")
	 * @param value the value of the variable (e.g. "green")
	 */
	protected void setLine(String subject, String variable, String value)
	{
		//find the line containing the subject
		int subjectLine = findSubjectLine(subject);
		if (subjectLine == -1)
		{
			addSubjectLine(subject);
			subjectLine = lines.size()-1;
		}
		//find the last line of the subject
		int endOfSubject = endOfSubject(subjectLine);
		//find the assignment within the subject
		int lineNumber =  findAssignmentBetween(variable,subjectLine,endOfSubject);

		//if an assignment line doesn't exist, insert one, else change the existing one
		if (lineNumber == -1)
			lines.insertElementAt(variable+"="+value,endOfSubject);
		else
			lines.setElementAt(variable+"="+value,lineNumber);
	}

	/**
	 * find the line containing a variable within a subject
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @param variable the variable name (e.g. "Color")
	 * @return the line number of the assignment, -1 if not found
	 */
	protected int findAssignmentLine(String subject, String variable)
	{
		int start = findSubjectLine(subject);
		int end = endOfSubject(start);
		return findAssignmentBetween(variable,start,end);
	}

	/**
	 * find the line containing a variable within a range of lines
	 * @param variable the variable name (e.g. "Color")
	 * @param start the start of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 * @return the line number of the assignment, -1 if not found
	 */
	protected int findAssignmentBetween(String variable, int start, int end)
	{
		for (int i=start;i<end;i++)
		{
			if (lines.elementAt(i).startsWith(variable+"="))
				return i;
		}
		return -1;
	}

	/**
	 * add a subject line to the end of the lines vector
	 * @param subject the subject heading (e.g. "Widget Settings")
	 */
	protected void addSubjectLine(String subject)
	{
		lines.addElement("["+subject+"]");
	}

	/**
	 * find a subject line within the lines vector
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @return the line number of the subject, -1 if not found
	 */
	protected int findSubjectLine(String subject)
	{
		String line;
		String formattedSubject = "["+subject+"]";
		for (int i=0;i<lines.size();i++)
		{
			line = lines.elementAt(i);
			if (formattedSubject.equals(line))  return i;
		}
		return -1;
	}


	/**
	 * find the line number which is 1 past the last assignment in a subject
	 * starting at a given line
	 * @param start the line number at which to start looking
	 * @return the line number of the last assignment + 1
	 */
	protected int endOfSubject(int start)
	{
		int endIndex = start+1;
		if (start>=lines.size()) return lines.size();
		for (int i=start+1;i<lines.size();i++)
		{
			if (isanAssignment(lines.elementAt(i)))
				endIndex = i+1;
			if (isaSubject(lines.elementAt(i)))
				return endIndex;
		}
		return endIndex;
	}

	/**
	 * does the line represent an assignment?
	 * @param line a string representing a line from an INI file
	 * @return true if line is an assignment
	 */
	protected boolean isanAssignment(String line)
	{
		if ((line.indexOf("=")!=-1) && (!line.startsWith(";")))
			return true;
		else
			return false;
	}

	/**
	 * get a copy of the lines vector
	 */
	@SuppressWarnings("unchecked")
	public Vector<String> getLines()
	{
		return (Vector<String>)lines.clone();
	}

	/**
	 * get a vector containing all variables in a subject
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @return a list of variables, empty vector if subject not found
	 */
	public String[] getVariables(String subject)
	{
		String[] v;
		int index = subjects.indexOf(subject);
		if (index != -1)
		{
			Vector<?> vars = (variables.elementAt(index));
			v = new String[vars.size()];
			vars.copyInto(v);
			return v;
		}
		else
		{
			v = new String[0]; 
			return v;
		}
	}

	/**
	 * get an array containing all subjects
	 * @return a list of subjects
	 */
	public String[] getSubjects()
	{
		String[] s = new String[subjects.size()];
		subjects.copyInto(s);
		return s;
	}

	/**
	 * method to check if a subject is defined
	 *@param subject to check for
	 *@return if the subject is defined
	 */
	public boolean hasSubject(String subject)
	{

		int subjectIndex = subjects.indexOf(subject);
		if (subjectIndex == -1)
			return false;
		return true;
	}

	/**
	 * check to see if a variable is boolean true
	 * @param subject the subject of the heading
	 * @param variable the variable name
	 * @return true if value is "1", "yes", or "true"
	 */
	public boolean isTrue(String subject, String variable) {
		String s=getValue(subject,variable);

		if ( null == s ) {
			if ( null != memcache ) {
				/* add this value to memcache */			
				String key="PCTAP_" + subject + "_" + variable;
				key = key.toUpperCase();
				memcache.set(key,memcacheTimeout,"0");
				System.err.println("# IniFile added key=" + key + " value=" + "0");
			}

			return false;
		}

		s=s.toLowerCase();

		if ( 0 == s.compareTo("1") || 0 == s.compareTo("yes") || 0 == s.compareTo("true") ) {
			if ( null != memcache ) {
				/* add this value to memcache */			
				String key="PCTAP_" + subject + "_" + variable;
				key = key.toUpperCase();
				memcache.set(key,memcacheTimeout,"1");
				System.err.println("# IniFile added key=" + key + " value=" + "1");
			}

			return true;
		}

		if ( null != memcache ) {
			/* add this value to memcache */			
			String key="PCTAP_" + subject + "_" + variable;
			key = key.toUpperCase();
			memcache.set(key,memcacheTimeout,"0");
			System.err.println("# IniFile added key=" + key + " value=" + "0");
		}


		return false;
	}

	/**
	 * get the value of a variable within a subject
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @param variable the variable name (e.g. "Color")
	 * @return the value of the variable (e.g. "green"), empty string if not found
	 */
	public String getValueReal(String subject, String variable)
	{
		int subjectIndex = subjects.indexOf(subject);
		if (subjectIndex == -1)
			return null;
		Vector<?> valVector = (values.elementAt(subjectIndex));
		Vector<?> varVector = (variables.elementAt(subjectIndex));
		int valueIndex = varVector.indexOf(variable) ;
		if (valueIndex != -1)
		{
			return (String)(valVector.elementAt(valueIndex));
		}
		return null;
	}

	public String getValue(String subject, String variable) {
		String val=getValueReal(subject, variable);

		if ( null != memcache ) {
			/* add this value to memcache */			
			String key="PCTAP_" + subject + "_" + variable;
			key = key.toUpperCase();
			memcache.set(key,memcacheTimeout, val);
			System.err.println("# IniFile added key=" + key + " value=" + val);
		}

		return val;
	}

	/**
	 * get the value of a variable within a subject
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @param variable the variable name (e.g. "Color")
	 *@parm default value to return if null
	 * @return the value of the variable (e.g. "green"), empty string if not found
	 */
	public String getValueSafeReal(String subject, String variable, String d)
	{
		int subjectIndex = subjects.indexOf(subject);
		if (subjectIndex == -1)
			return d;
		Vector<?> valVector = (values.elementAt(subjectIndex));
		Vector<?> varVector = (variables.elementAt(subjectIndex));
		int valueIndex = varVector.indexOf(variable) ;
		if (valueIndex != -1)
		{
			return (String)(valVector.elementAt(valueIndex));
		}
		return d;
	}

	public String getValueSafe(String subject, String variable, String d) {
		String val=getValueSafeReal(subject, variable,d);

		if ( null != memcache ) {
			/* add this value to memcache */			
			String key="PCTAP_" + subject + "_" + variable;
			key = key.toUpperCase();
			memcache.set(key,memcacheTimeout, val);
			System.err.println("# IniFile added key=" + key + " value=" + val);
		}

		return val;
	}


	/**
	 * delete variable within a subject
	 * @param subject the subject heading (e.g. "Widget Settings")
	 * @param variable the variable name (e.g. "Color")
	 */
	public void deleteValue(String subject, String variable)
	{
		int subjectIndex = subjects.indexOf(subject);
		if (subjectIndex == -1)
			return;

		Vector<?> valVector = (values.elementAt(subjectIndex));
		Vector<?> varVector = (variables.elementAt(subjectIndex));

		int valueIndex = varVector.indexOf(variable) ;
		if (valueIndex != -1)
		{
			//delete from variables and values vectors
			valVector.removeElementAt(valueIndex);
			varVector.removeElementAt(valueIndex);
			//delete from lines vector
			int assignmentLine = findAssignmentLine(subject, variable);
			if (assignmentLine != -1)
			{
				lines.removeElementAt(assignmentLine);
			}
			//if the subject is empty, delete it
			if (varVector.size()==0)
			{
				deleteSubject(subject);
			}
			if (saveOnChange) saveFile();
		}
	}

	/**
	 * delete a subject and all its variables
	 * @param subject the subject heading (e.g. "Widget Settings")
	 */
	public void deleteSubject(String subject)
	{
		int subjectIndex = subjects.indexOf(subject);
		if (subjectIndex == -1)
			return;
		//delete from subjects, variables and values vectors
		values.removeElementAt(subjectIndex);
		variables.removeElementAt(subjectIndex);
		subjects.removeElementAt(subjectIndex);
		//delete from lines vector
		int start = findSubjectLine(subject);
		int end = endOfSubject(start);
		for (int i=start;i<end;i++)
		{
			lines.removeElementAt(start);
		}
		if (saveOnChange) saveFile();
	}


	/**
	 * save the lines vector back to the INI file
	 */
	public void saveFile() 
	{
		try
		{
			DataOutputStream outFile = new DataOutputStream(
					new BufferedOutputStream(
							new FileOutputStream(fileName)));
			for (int i=0;i<lines.size();i++)
			{
				outFile.writeBytes((lines.elementAt(i))+System.getProperty("line.separator"));
			}
			outFile.close();
		}
		catch (IOException e)
		{
			System.out.println("IniFile save failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * clean up
	 */
	protected void finalize()
	{
		saveFile();
	}

}


