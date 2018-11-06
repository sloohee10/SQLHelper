package com.nsma;

/**
 *
 * @author Saleh Haddawi
 */
public interface SQLHelperUpdateStatment {

    public SQLHelperUpdateStatment setCols(String columns);
    
    public SQLHelperUpdateStatment setValues(Object... values);
    
    public SQLHelperUpdateStatment setValues(SQLHelperValue valueObject);
        
    public SQLHelperUpdateStatment setCol(String col,Object value)throws Exception;
    
    public SQLHelperUpdateStatment setCondition(String condition,Object... values);
    
    public SQLHelperUpdateStatment reset();
    
    public int execute() throws Exception;
    
    public long executeLarge() throws Exception;
}
