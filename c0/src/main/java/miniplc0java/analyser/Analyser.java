package miniplc0java.analyser;

import miniplc0java.error.AnalyzeError;
import miniplc0java.error.CompileError;
import miniplc0java.error.ErrorCode;
import miniplc0java.error.ExpectedTokenError;
import miniplc0java.error.TokenizeError;
import miniplc0java.navm.*;
import miniplc0java.tokenizer.Token;
import miniplc0java.tokenizer.TokenType;
import miniplc0java.tokenizer.Tokenizer;

import java.io.IOException;
import java.util.*;

import static miniplc0java.error.ErrorCode.*;

public final class Analyser {

    Tokenizer tokenizer;

    /**
     * 新添加的数据结构
     */
    LocalSymbolTable localSymbolTable;
    GlobalSymbolTable globalSymbolTable;
    FunctionTable functionTable;
    LocalVariableStack localVariableStack;
    ArrayList<NavmInstruction> instructions;

    /** 为了保证call的参数是正确的 设置这2个变量*/
    HashMap<String, Integer> callParam;
    int callOffset;

    OZero oZero;
    GlobalStack globalStack;
    ParamAndReturnValueStack paramAndReturnValueStack;

    int expressionFlag = 0;
    int paramFlag = 0;
    int assignFlag = 0;
    int retFlag = 0;
    int compareFlag = 0;
    int argaFlag = 1;

    /** 当前偷看的 token */
    Token peekedToken = null;

    public Analyser(Tokenizer tokenizer) throws AnalyzeError {
        this.tokenizer = tokenizer;
        this.instructions = new ArrayList<>();

        this.localSymbolTable = new LocalSymbolTable(1);
        this.globalStack = new GlobalStack();
        this.localVariableStack = new LocalVariableStack();
        this.functionTable = new FunctionTable();
        this.paramAndReturnValueStack = new ParamAndReturnValueStack();

        this.callOffset = 0;
        this.callParam = new HashMap<>();

        ArrayList<String> arrayList1 = new ArrayList<>();
        arrayList1.add("int");

        this.functionTable.addNewFunction(new Function("getint", 0, null, "int"));
        this.functionTable.addNewFunction(new Function("putint", 1, arrayList1, "void"));
        this.functionTable.addNewFunction(new Function("putln", 0, null, "void"));
        this.functionTable.addNewFunction(new Function("putstr", 1, null, "void"));
        this.functionTable.addNewFunction(new Function("putchar", 1, null, "void"));

        GlobalSymbolTable globalSymbolTable = new GlobalSymbolTable(0);
        setGlobalSymbolTable(globalSymbolTable);
        this.globalSymbolTable.addGlobalVariable(new Token(TokenType.IDENT, "getint"), new SymbolEntry(true, true, "int"));
        this.globalSymbolTable.addGlobalVariable(new Token(TokenType.IDENT, "putint"), new SymbolEntry(true, true, "void"));
        this.globalSymbolTable.addGlobalVariable(new Token(TokenType.IDENT, "putln"), new SymbolEntry(true, true, "void"));
        this.globalSymbolTable.addGlobalVariable(new Token(TokenType.IDENT, "putstr"), new SymbolEntry(true, true, "void"));
        this.globalSymbolTable.addGlobalVariable(new Token(TokenType.IDENT, "putchar"), new SymbolEntry(true, true, "void"));

        this.oZero = new OZero();
    }

    public List<NavmInstruction> analyse() throws CompileError, IOException {
        throw new Error();
//        analyseProgram();
//        return instructions;
    }

    /**
     * 查看下一个 Token
     *
     * @return
     * @throws TokenizeError
     */
    private Token peek() throws TokenizeError {
        if (peekedToken == null) {
            peekedToken = tokenizer.nextToken();
        }
        return peekedToken;
    }

    /**
     * 获取下一个 Token
     *
     * @return
     * @throws TokenizeError
     */
    private Token next() throws TokenizeError {
        if (peekedToken != null) {
            var token = peekedToken;
            peekedToken = null;
            return token;
        } else {
            return tokenizer.nextToken();
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则返回 true
     *
     * @param tt
     * @return
     * @throws TokenizeError
     */
    private boolean check(TokenType tt) throws TokenizeError {
        var token = peek();
        return token.getTokenType() == tt;
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回这个 token
     * @param tt 类型
     * @return 如果匹配则返回这个 token，否则返回 null
     * @throws TokenizeError
     */
    private Token nextIf(TokenType tt) throws TokenizeError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            return null;
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回，否则抛出异常
     *
     * @param tt 类型
     * @return 这个 token
     * @throws CompileError 如果类型不匹配
     */
    private Token expect(TokenType tt) throws CompileError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            throw new ExpectedTokenError(tt, token);
        }
    }

    /**
     * 表达式
     * 语义check
     * @throws CompileError
     */
    private int analyseExpression(int level) throws CompileError {
        int numOfInstructions = 0;
        Token tokenForError = null;
        numOfInstructions += analyseItemExpression(level);

        while (check(TokenType.MINUS) || check(TokenType.PLUS)) {
            expressionFlag = 1;
            int flag = 0;
            if (check(TokenType.MINUS)) {
                flag = 1;
            } else {
                flag = 2;
            }

            tokenForError = next();

            numOfInstructions += analyseItemExpression(level);
            if (flag == 1) {
                addSubInstruction();
                numOfInstructions ++;
            } else if (flag == 2 ){
                addAddInstruction();
                numOfInstructions ++;
            }
            expressionFlag = 0;
        }
        return numOfInstructions;
    }

    /**
     * 项
     * 语义check
     * @param
     * @throws CompileError
     */
    public int analyseItemExpression(int level) throws CompileError {
        int numOfInstructions = 0;
        Token tokenForError = null;
        numOfInstructions += analyseUnaryExpression(level);

        while (check(TokenType.MUL) || check(TokenType.DIV)) {
            expressionFlag = 1;
            int flag = 0;
            if (check(TokenType.MUL)) {
                flag = 1;
            } else {
                flag = 2;
            }
            tokenForError = next();
            numOfInstructions += analyseUnaryExpression(level);
            if (flag == 1) {
                addMulInstruction();
                numOfInstructions ++;
            } else if (flag == 2) {
                addDivInstruction();
                numOfInstructions ++;
            }
        }
        return numOfInstructions;
    }

    /**
     *
     * 语义check
     * @param
     * @throws CompileError
     */
    public int analyseUnaryExpression(int level) throws CompileError {
        int numOfInstructions = 0;
        Token tokenForError = null;
        var minusToken = peek();
        if (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            tokenForError = next();
        }

        numOfInstructions += analyseFacterExpression(level);
        if (minusToken.getTokenType() == TokenType.MINUS) {
            addNegInstruction();
            numOfInstructions ++;
        }

        return numOfInstructions;
    }

    /**
     * group_expr
     *
     * @param
     * @throws CompileError
     */
    public int analyseFacterExpression(int level) throws CompileError {
        int numOfInstructions = 0;

        if (check(TokenType.L_PAREN)) {
            expect(TokenType.L_PAREN);
            numOfInstructions += analyseExpression(level);
            expect(TokenType.R_PAREN);
        } else if (check(TokenType.IDENT)) {
            Token ident = next();
            int searchResult = searchForSymbol(level, String.valueOf(ident.getValue()));
            if (searchResult == 0) {
                throw new AnalyzeError(NotDeclared, ident.getStartPos());
            } else if (searchResult == 1) {
                if (this.localVariableStack.getOffset(String.valueOf(ident.getValue())) != -1) {
                    addLocaInstruction(ident);
                    numOfInstructions ++;
                    if (!check(TokenType.ASSIGN)) {
                        addLoadInstruction();
                        numOfInstructions ++;
                    }
                } else if (this.paramAndReturnValueStack.getOffset(String.valueOf(ident.getValue())) != -1) {
                    addArgaInstruction(ident);
                    numOfInstructions ++;
                    if (!check(TokenType.ASSIGN)) {
                        addLoadInstruction();
                        numOfInstructions ++;
                    }
                }
            } else if (searchResult == 2) {
                if (this.globalStack.getGlobalStackOffset(String.valueOf(ident.getValue())) != -1) {
                    if (!functionTable.hasFunction(String.valueOf(ident.getValue()))) {
                        addGlobaInstruction(ident);
                        numOfInstructions ++;
                        if (!check(TokenType.ASSIGN)) {
                            addLoadInstruction();
                            numOfInstructions ++;
                        }
                    }
                }
            }

            /** 加指令 */

            if (check(TokenType.ASSIGN)) {
                next();
                expressionFlag = 1;
                assignFlag = 1;

                if (judgeConst(level, String.valueOf(ident.getValue()))) {
                    throw new AnalyzeError(AssignToConstant, ident.getStartPos());
                }

                /** 加指令 */

                numOfInstructions += analyseExpression(level);
                addStoreInstruction();
                numOfInstructions ++;

            } else if (check(TokenType.L_PAREN)) {
                int libraryFlag = 0;
                int stackAllocParam = 0;
                int numOfParam = 0;
                if (!searchForFunction(String.valueOf(ident.getValue()))) {
                    throw new AnalyzeError(NotDeclared, ident.getStartPos());
                }

                String returnType = getFunctionReturnType(String.valueOf(ident.getValue()));
                if (returnType.equals("int")) {
                    addStackAllocInstruction(1);
                    stackAllocParam = 1;
                    numOfInstructions ++;
                } else if (returnType.equals("void")) {

                    if (expressionFlag == 1) {
                        throw new AnalyzeError(WrongReturnType, ident.getStartPos());
                    }
                    if (paramFlag == 1) {
                        throw new AnalyzeError(WrongReturnType, ident.getStartPos());
                    }

                    addStackAllocInstruction(0);
                    numOfInstructions ++;
                } else {
                    throw new AnalyzeError(WrongReturnType, ident.getStartPos());
                }

                int expectNumOfParam = getNumOfFunctionParam(String.valueOf(ident.getValue()));
                expect(TokenType.L_PAREN);
                if (!check(TokenType.R_PAREN)) {
                    paramFlag ++;
                    int retArray[] = analyseCallParamList(level);
                    numOfParam = retArray[0];
                    numOfInstructions += retArray[1];
                }
                if (expectNumOfParam != numOfParam) {
                    throw new AnalyzeError(ErrorCode.WrongNumOfParam, ident.getStartPos());
                }

                expect(TokenType.R_PAREN);

                if (isLibraryFunction(String.valueOf(ident.getValue()))) {
                    GlobalDef globalDef = new GlobalDef();
                    globalDef.setIs_const(0x01);
                    globalDef.setValue(generateGlobalDefFunctionName(String.valueOf(ident.getValue())));
                    addGlobalDefToOzero(globalDef);

                    addGlobalToStack(String.valueOf(ident.getValue()));
                    libraryFlag = 1;
                }
                if (libraryFlag == 0) {
                    int offset = this.callParam.get(String.valueOf(ident.getValue()));
                    addCallInstruction(offset);
                    numOfInstructions ++;
                } else if (libraryFlag == 1) {
                    int offset = globalStack.getGlobalStackOffset(String.valueOf(ident.getValue()));
                    addCallnameInstruction(offset);
                    numOfInstructions ++;
                }

                if (paramFlag == 0 && assignFlag != 1 && compareFlag != 1 && stackAllocParam != 0 && retFlag != 1) {
                    addPopNInstruction();
                    numOfInstructions ++;
                }

            }

        } else if (check(TokenType.UINT_LITERAL)) {
            Token uintLiteral = next();
            addPushInstruction(Integer.parseInt(String.valueOf(uintLiteral.getValue())));
            numOfInstructions ++;
        } else if (check(TokenType.STRING_LITERAL)) {

            Token stringLiteral = next();
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 1; i < String.valueOf(stringLiteral.getValue()).length() - 1; i ++) {
                stringBuilder.append(String.valueOf(stringLiteral.getValue()).charAt(i));
            }

            GlobalDef globalDef = new GlobalDef();
            globalDef.setIs_const(0x01);
            globalDef.setValue(generateGlobalDefFunctionName(stringBuilder.toString()));
            addGlobalDefToOzero(globalDef);

            addGlobalToStack(stringBuilder.toString());
            addPushInstruction(getGlobalDefIndex());

        }

        return numOfInstructions;
    }

    /**
     * 语义check
     * @param
     * @return
     * @throws CompileError
     */
    public int[] analyseCallParamList(int level) throws CompileError {
        int retArray[] = new int[2];
        int numOfInstructions = 0;
        int numOfParam = 1;
        numOfInstructions += analyseExpression(level);

        while (check(TokenType.COMMA)) {
            next();
            numOfParam ++;
            numOfInstructions += analyseExpression(level);
        }
        paramFlag --;
        retArray[0] = numOfParam;
        retArray[1] = numOfInstructions;
        return retArray;
    }
    /**
     * 语句
     * <语句> ::= 表达式语句 | 声明语句 | if语句 | while语句 | return语句 | 代码块 | 空语句
     * @throws CompileError
     */
    public int analyseStatement(int level) throws CompileError {
        int numOfInstructions = 0;
        if (check(TokenType.IF_KW)) {
            numOfInstructions += analyseIfStatement(level);
        } else if (check(TokenType.WHILE_KW)) {
            numOfInstructions += analyseWhileStatement(level);
        } else if (check(TokenType.RETURN_KW)) {
            numOfInstructions += analyseReturnStatement(level);
        } else if (check(TokenType.L_BRACE)) {
            numOfInstructions += analyseBlockStatement(level);
        } else if (check(TokenType.SEMICOLON)) {
            numOfInstructions += analyseEmptyStatement();
        } else if (check(TokenType.LET_KW) || check(TokenType.CONST_KW)) {
            numOfInstructions += analyseDeclareStatement(level);
        } else {
            numOfInstructions += analyseExpressionStatement(level);
        }
        return numOfInstructions;
    }

    /**
     * 语义check
     * 函数
     * function_param -> 'const'? IDENT ':' ty
     * function_param_list -> function_param (',' function_param)*
     * function -> 'fn' IDENT '(' function_param_list? ')' '->' ty block_stmt
     * //               ^~~~      ^~~~~~~~~~~~~~~~~~~~          ^~ ^~~~~~~~~~
     * //               |              |                        |  |
     * //               function_name  param_list     return_type  function_body
     * @throws CompileError
     *
     */
    public void analyseFunction() throws CompileError {
        FunctionDef functionDef = new FunctionDef();
        GlobalDef globalDef = new GlobalDef();

        int numOfParam = 0;
        this.retFlag = 0;
        ArrayList<String> typeOfParam = new ArrayList<>();

        expect(TokenType.FN_KW);

        var functionName = expect(TokenType.IDENT);

        /**
         * 如果当前函数表里已经有了该函数的名字 就抛异常
         * 否则 向函数名称表里添加该函数的名字
         */
        if (hasFunction(String.valueOf(functionName.getValue()))) {
            throw new AnalyzeError(DuplicateDeclaration, functionName.getStartPos());
        }

        expect(TokenType.L_PAREN);

        if (isStartOfFunctionParamList()) {
            numOfParam = analyseFunctionParamList(1, typeOfParam);
        }

        expect(TokenType.R_PAREN);

        expect(TokenType.ARROW);

        var functionReturnType = analyseTy();

        if (functionReturnType.equals("int")) {
            argaFlag = 0;
        }

        localSymbolTable.setFunctionReturnType(functionReturnType);

        var newFunction = new Function(String.valueOf(functionName.getValue()), numOfParam, typeOfParam, functionReturnType);
        addNewFunction(newFunction);

        /** 保证call指令参数的正确 */
        this.callOffset ++;
        this.callParam.put(String.valueOf(functionName.getValue()), callOffset);

        this.globalSymbolTable.addGlobalVariable(functionName, new SymbolEntry(true, true, functionReturnType));

        analyseBlockStatement(1);
        addGlobalToStack(String.valueOf(functionName.getValue()));

        if (retFlag != 1) {
            if (functionReturnType.equals("int")) {
                throw new AnalyzeError(NeedReturnStatement, functionName.getStartPos());
            }
            addReturnInstruction();
            retFlag = 0;
        }

        if (this.instructions.size() == 0 ||
                this.instructions.get(this.instructions.size() - 1).getOpcode() != 0x49) {
            addReturnInstruction();
        }

        /**
         * 为每个函数创建一个FunctionDef对象 和 GlobalDef对象 初始化各值 然后加入oZero
         */
        addFunctionDefToOzero(functionDef);
        addGlobalDefToOzero(globalDef);
        functionDef.setParam_slots(numOfParam);
        functionDef.setLoc_slots(this.localSymbolTable.getNumOflocalVariables() - numOfParam);
        functionDef.setReturn_slots(this.localSymbolTable.getReturnTypeOfFunction());
        functionDef.setName(globalStack.getGlobalStackOffset(String.valueOf(functionName.getValue())));
        functionDef.setBody(this.instructions);

        globalDef.setIs_const(0x01);
        globalDef.setValue(generateGlobalDefFunctionName(String.valueOf(functionName.getValue())));

        System.out.println("fn [" + globalStack.getGlobalStackOffset(String.valueOf(functionName.getValue())) + "] "
        + (localSymbolTable.getSizeOfMap() - numOfParam) + " " + numOfParam + " -> " +this.localSymbolTable.getReturnTypeOfFunction()
                + " {"
        );
        printInstructions(functionDef);
        System.out.println("}");
        System.out.println();
        clearLocalSymbolTable();
        clearLocalVariableStack();
        clearInstructions();
        clearParamAndReturnValueStack();

        argaFlag = 1;
    }

    /**
     * 函数参数
     * function_param -> 'const'? IDENT ':' ty
     * @throws CompileError
     *
     * 检查函数参数是否重复
     * 并将参数填入本函数的符号表
     */
    public String analyseFunctionParam(int level) throws CompileError {
        LocalSymbolTable localSymbolTable = this.localSymbolTable;
        int constFlag = 0;
        if (check(TokenType.CONST_KW)) {
            constFlag = 1;
            next();
        }

        var token = expect(TokenType.IDENT);
        SymbolEntry symbolEntry;
        if (constFlag == 1) {
            symbolEntry = new SymbolEntry(true, true);
        } else {
            symbolEntry = new SymbolEntry(false, true);
        }

        if (!localSymbolTable.duplicateSymbol(String.valueOf(token.getValue()))) {
            localSymbolTable.addLocalVariable(token, symbolEntry);
        } else {
            throw new AnalyzeError(DuplicateDeclaration, token.getStartPos());
        }

        /** 将参数加入参数和返回值栈 */
        addParamToStack(String.valueOf(token.getValue()));


        expect(TokenType.COLON);
        String tokenType = analyseTy();
        if (!tokenType.equals("int")) {
            throw new AnalyzeError(ErrorCode.WrongType, token.getStartPos());
        }
        symbolEntry.setType(tokenType);
        return tokenType;
    }

    /**
     * 语义check
     * 函数参数列表 function_param_list
     * function_param_list -> function_param (',' function_param)*
     * @param
     * @throws CompileError
     */
    public int analyseFunctionParamList(int level, ArrayList<String> typeOfParam) throws CompileError{
        int numOfParam = 0;

        var paramType = analyseFunctionParam(level);
        numOfParam ++;
        typeOfParam.add(paramType);

        while (nextIf(TokenType.COMMA) != null) {
            paramType = analyseFunctionParam(level);
            numOfParam ++;
            typeOfParam.add(paramType);
        }

        return numOfParam;
    }

    /**
     * 函数参数开头
     * @return
     * @throws CompileError
     */
    public boolean isStartOfFunctionParamList() throws CompileError{
        if (check(TokenType.CONST_KW) || check(TokenType.IDENT)) {
            return true;
        }
        return false;
    }

    /**
     * 判断一个 token 是不是二值运算符号
     * @return
     * @throws TokenizeError
     */
    public boolean isBinaryOperator() throws TokenizeError {
        if ( check(TokenType.EQ) || check(TokenType.NEQ) || check(TokenType.LT)
            || check(TokenType.LE) || check(TokenType.GT) || check(TokenType.GE)) {
            return true;
        }
        return false;
    }

    /**
     * if 语句
     * if_stmt -> 'if' expr block_stmt ('else' (block_stmt | if_stmt))?
     * @throws CompileError
     */
    public int analyseIfStatement(int level) throws CompileError {
        int numOfInstructions = 0;
        expect(TokenType.IF_KW);

        compareFlag = 1;

        if (check(TokenType.L_PAREN)) {
            expect(TokenType.L_PAREN);
        }

        int tmpNumOfInstructions = analyseConditionStatement(level);

        if (check(TokenType.R_PAREN)) {
            expect(TokenType.R_PAREN);
        }

        addBrTrueInstruction(1);
        numOfInstructions ++;

        compareFlag = 0;

        NavmInstruction firstBrInstruction = addBrInstruction(0);
        numOfInstructions ++;

        int numOfBlockInstructions = analyseBlockStatement(level);
        numOfInstructions += numOfBlockInstructions;

        setBrInstructionParam(firstBrInstruction, numOfBlockInstructions + 1);

        NavmInstruction secondBrInstruction = addBrInstruction(0);
        numOfInstructions ++;

        int numOfElseBlockInstructions = 0;
        if (check(TokenType.ELSE_KW)) {
            next();
            if (check(TokenType.L_BRACE)) {
                numOfElseBlockInstructions += analyseBlockStatement(level);
                numOfInstructions += numOfElseBlockInstructions;
            } else {
                numOfElseBlockInstructions += analyseIfStatement(level);
                numOfInstructions += numOfElseBlockInstructions;
            }
        }

        setBrInstructionParam(secondBrInstruction, numOfElseBlockInstructions);

        return numOfInstructions + tmpNumOfInstructions;
    }

    public int analyseConditionStatement(int level) throws CompileError{
        int numOfInstructions = 0;
        numOfInstructions += analyseExpression(level);
        if (isBinaryOperator()) {
            Token binaryOperator = next();

            numOfInstructions += analyseExpression(level);

            addCmpiInstruction();
            numOfInstructions ++;
            if (binaryOperator.getTokenType() == TokenType.LT) {
                addSetLtInstruction();
                numOfInstructions ++;
            } else if (binaryOperator.getTokenType() == TokenType.LE) {
                addSetGtInstruction();
                addNotInstruction();
                numOfInstructions += 2;
            } else if (binaryOperator.getTokenType() == TokenType.GT) {
                addSetGtInstruction();
                numOfInstructions ++;
            } else if (binaryOperator.getTokenType() == TokenType.GE) {
                addSetLtInstruction();
                addNotInstruction();
                numOfInstructions += 2;
            } else if (binaryOperator.getTokenType() == TokenType.EQ) {
                addNotInstruction();
                numOfInstructions ++;
            }

        }
        return numOfInstructions;
    }
    /**
     * 代码块
     * block_stmt -> '{' stmt* '}'
     * @throws CompileError
     */
    public int analyseBlockStatement(int level) throws CompileError {
        int numOfInstructions = 0;
        expect(TokenType.L_BRACE);

        while (isStartOfStatement()) {
            numOfInstructions += analyseStatement(level);
        }

        expect(TokenType.R_BRACE);
        return numOfInstructions;
    }


    /**
     * 判断一个 token 是不是语句的开头
     * @return
     * @throws TokenizeError
     */
    public boolean isStartOfStatement() throws TokenizeError {
        if (check(TokenType.SEMICOLON) || check(TokenType.L_BRACE) || check(TokenType.RETURN_KW) || check(TokenType.WHILE_KW)
            || check(TokenType.IF_KW) || check(TokenType.LET_KW) || check(TokenType.CONST_KW) || check(TokenType.MINUS)
                || check(TokenType.IDENT) || check(TokenType.L_PAREN) || isStartOfExpression()) {
            return true;
        }
        return false;
    }

    /**
     * while 语句
     * while_stmt -> 'while' expr block_stmt
     * @throws CompileError
     */
    public int analyseWhileStatement(int level) throws CompileError {
        int numOfInstructions = 0;
        expect(TokenType.WHILE_KW);
        addBrInstruction(0);
        numOfInstructions ++;

        if (check(TokenType.L_PAREN)) {
            expect(TokenType.L_PAREN);
        }

        compareFlag = 1;
        int numOfConditionInstructions = 0;
        numOfConditionInstructions = analyseConditionStatement(level);

        numOfInstructions += numOfConditionInstructions;
        compareFlag = 0;

        if (check(TokenType.R_PAREN)) {
            expect(TokenType.R_PAREN);
        }

        addBrTrueInstruction(1);
        numOfInstructions ++;

        NavmInstruction firstBrInstruction = addBrInstruction(0);
        numOfInstructions ++;

        int numOfBlockInstructions = 0;
        numOfBlockInstructions = analyseBlockStatement(level);
        numOfInstructions += numOfBlockInstructions;
        setBrInstructionParam(firstBrInstruction, numOfBlockInstructions + 1);


        numOfInstructions ++;
        //while 的奇怪跳转
        addBrInstruction(- numOfInstructions + 1);
        return numOfInstructions;
    }

    /**
     * return 语句
     * return_stmt -> 'return' expr? ';'
     * @throws CompileError
     */
    public int analyseReturnStatement(int level) throws CompileError {
        int numOfInstructions = 0;
        retFlag = 1;
        expect(TokenType.RETURN_KW);
        LocalSymbolTable localSymbolTable = this.localSymbolTable;

        if (localSymbolTable.getFunctionReturnType().equals("void")) {

            expect(TokenType.SEMICOLON);
            expressionFlag = 0;
            assignFlag = 0;
            addReturnInstruction();
            numOfInstructions ++;

            return numOfInstructions;
        }

        addArga0Instruction();
        numOfInstructions ++;
        if (!isStartOfExpression()) {
            throw new AnalyzeError(NeedExpression);
        }
        numOfInstructions += analyseExpression(level);

        expect(TokenType.SEMICOLON);
        expressionFlag = 0;
        paramFlag = 0;
        assignFlag = 0;

        addStoreInstruction();
        addReturnInstruction();
        numOfInstructions += 2;
        return numOfInstructions;
    }

    /**
     * 判断一个 token 是否是表达式的开头
     * @return
     * @throws TokenizeError
     */
    public boolean isStartOfExpression() throws TokenizeError {
        if (check(TokenType.MINUS) || check(TokenType.IDENT) || check(TokenType.L_PAREN) || check(TokenType.UINT_LITERAL)) {
            return true;
        }
        return false;
    }

    /**
     * 空语句
     * empty_stmt -> ';'
     * @throws CompileError
     */
    public int analyseEmptyStatement() throws CompileError {
        expect(TokenType.SEMICOLON);
        expressionFlag = 0;
        paramFlag = 0;
        assignFlag = 0;
        return 0;
    }

    /**
     * 声明语句
     * let_decl_stmt -> 'let' IDENT ':' ty ('=' expr)? ';'
     * const_decl_stmt -> 'const' IDENT ':' ty '=' expr ';'
     * decl_stmt -> let_decl_stmt | const_decl_stmt
     * @throws CompileError
     *
     * 完成了符号表管理
     */
    public int analyseDeclareStatement(int level) throws CompileError {
        LocalSymbolTable localSymbolTable = this.localSymbolTable;
        GlobalSymbolTable globalSymbolTable = this.globalSymbolTable;
        int numOfInstructions = 0;

        var tokenForErrorPos = peek();
        if (check(TokenType.LET_KW)) {
            next();

            var token = expect(TokenType.IDENT);
            if (localSymbolTable.duplicateSymbol(String.valueOf(token.getValue()))) {
                throw new AnalyzeError(DuplicateDeclaration, token.getStartPos());
            }
            var symbolEntry = new SymbolEntry(false, false);

            /** 根据传进来的level决定添加到局部变量栈还是全局变量栈 */
            if (level == 1) {
                addLocalVariableToStack(String.valueOf(token.getValue()));
            } else if (level == 0) {
                GlobalDef globalDef = new GlobalDef();
                globalDef.setIs_const(0);
                globalDef.setValue(generateGlobalVariableList());

                addGlobalDefToOzero(globalDef);
                addGlobalToStack(String.valueOf(token.getValue()));

            }

            expect(TokenType.COLON);

            var tokenType = analyseTy();
            if (!tokenType.equals("int")) {
                throw new AnalyzeError(ErrorCode.WrongType, token.getStartPos());
            }
            symbolEntry.setType(tokenType);

            if (nextIf(TokenType.ASSIGN) != null) {
                expressionFlag = 1;
                assignFlag = 1;
                if (level == 1) {
                    addLocaInstruction(token);
                    numOfInstructions ++;
                } else if (level == 0) {
                    addGlobaInstruction(token);
                    numOfInstructions ++;
                }

                numOfInstructions += analyseExpression(level);
                symbolEntry.setInitialized(true);

                addStoreInstruction();
                numOfInstructions ++;

            }

            if (level == 1) {
                localSymbolTable.addLocalVariable(token, symbolEntry);
            } else if (level == 0) {
                globalSymbolTable.addGlobalVariable(token, symbolEntry);
            }


            expect(TokenType.SEMICOLON);
            expressionFlag = 0;
            paramFlag = 0;
            assignFlag = 0;
        } else if (check(TokenType.CONST_KW)) {
            next();

            var token = expect(TokenType.IDENT);
            var symbolEntry = new SymbolEntry(true, true, null);
            if (localSymbolTable.duplicateSymbol(String.valueOf(token.getValue()))) {
                throw new AnalyzeError(DuplicateDeclaration, token.getStartPos());
            }

            /** 根据传进来的level决定添加到局部变量栈还是全局变量栈 */
            if (level == 1) {
                addLocalVariableToStack(String.valueOf(token.getValue()));
            } else if (level == 0) {
                GlobalDef globalDef = new GlobalDef();
                globalDef.setIs_const(1);
                globalDef.setValue(generateGlobalVariableList());
                addGlobalDefToOzero(globalDef);
                addGlobalToStack(String.valueOf(token.getValue()));
            }

            expect(TokenType.COLON);

            var tokenType = analyseTy();
            if (!tokenType.equals("int")) {
                throw new AnalyzeError(ErrorCode.WrongType, token.getStartPos());
            }
            symbolEntry.setType(tokenType);

            expressionFlag = 1;
            assignFlag = 1;

            expect(TokenType.ASSIGN);

            if (level == 1) {
                addLocaInstruction(token);
                numOfInstructions ++;
            } else if (level == 0) {
                addGlobaInstruction(token);
                numOfInstructions ++;
            }

            numOfInstructions += analyseExpression(level);

            if (level == 1) {
                localSymbolTable.addLocalVariable(token, symbolEntry);
            } else if (level == 0) {
                globalSymbolTable.addGlobalVariable(token, symbolEntry);
            }

            addStoreInstruction();
            numOfInstructions ++;

            expect(TokenType.SEMICOLON);
            expressionFlag = 0;
            paramFlag = 0;
            assignFlag = 0;
        } else {
            throw new AnalyzeError(ErrorCode.InvalidInput, tokenForErrorPos.getStartPos());
        }
        return numOfInstructions;
    }

    /**
     * 分析类型 如果不是 int 或者 void 就抛异常
     * @throws CompileError
     */
    public String analyseTy() throws CompileError {
        var tokenForError = next();

        if (tokenForError.getValue().equals("int")) {
            return "int";
        } else if (tokenForError.getValue().equals("void")) {
            return "void";
        } else {
            throw new AnalyzeError(ErrorCode.InvalidInput, tokenForError.getStartPos());
        }

    }

    /**
     * 表达式语句
     * expr_stmt -> expr ';'
     * @throws CompileError
     */
    public int analyseExpressionStatement(int level) throws CompileError{
        var tokenForError = peek();
        int numOfInstructions = 0;
        numOfInstructions += analyseExpression(level);
        expect(TokenType.SEMICOLON);
        expressionFlag = 0;
        paramFlag = 0;
        assignFlag  = 0;
        return numOfInstructions;
    }

    /**
     * 判断下一个token是不是函数声明的头部
     * @return
     * @throws CompileError
     */
    public boolean isStartOfFunction() throws CompileError{
        if (check(TokenType.FN_KW)) {
            return true;
        }
        return false;
    }

    /**
     * 判断下一个token是不是声明语句的头部
     * @return
     * @throws CompileError
     */
    public boolean isStartOfDeclareStatement() throws CompileError {
        if (check(TokenType.LET_KW) || check(TokenType.CONST_KW)) {
            return true;
        }
        return false;
    }

    /**
     * 分析整个程序
     */
    public void analyseProgram() throws CompileError, IOException {

        while (isStartOfDeclareStatement()) {
            analyseDeclareStatement(0);
        }

        FunctionDef functionDef = new FunctionDef();
        GlobalDef globalDef = new GlobalDef();

        addFunctionDefToOzero(functionDef);
        functionDef.setParam_slots(0);
        functionDef.setLoc_slots(0);
        functionDef.setReturn_slots(0);

        functionDef.setBody(this.instructions);

        clearLocalSymbolTable();
        clearLocalVariableStack();
        clearInstructions();
        clearParamAndReturnValueStack();

        while (isStartOfFunction()) {
            analyseFunction();
        }

        int offset = callParam.get("main");

        String mainReturnType = getFunctionReturnType("main");
        int stackAllocParam = 0;
        if (mainReturnType.equals("int")) {
            stackAllocParam = 1;
        }
        functionDef.addStartInstruction(offset, stackAllocParam);

        functionDef.printInstructions();

        if (!hasMainFunction()) {
            throw new AnalyzeError(NeedMainFunction);
        }

        addGlobalDefToOzero(globalDef);
        globalDef.setIs_const(0x01);
        globalDef.setValue(generateGlobalDefFunctionName("_start"));
        addGlobalToStack("_start");
        functionDef.setName(globalStack.getGlobalStackOffset("_start"));
        this.oZero.outputOzero();
        this.oZero.o0OutputToFile();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println(this.globalStack.globalStack);
    }

    /**
     * 在当前函数名称表里面添加函数名称
     * @param function
     */
    public void addNewFunction(Function function) {
        this.functionTable.addNewFunction(function);
    }

    /**
     * 判断函数名称表里面是不是含有functionName
     * @param functionName
     * @return
     */
    public boolean hasFunction(String functionName) {
        return this.functionTable.hasFunction(functionName);
    }

    /**
     * 回溯 查找变量是否被定义
     * 没找到返回 0 在本函数符号表里找到返回 1 全局符号表里返回 2
     * 传入参数为 当前符号表 和 标识符的名称
     * @param
     * @param IdentName
     * @return
     */
    public int searchForSymbol(int level, String IdentName) {
        LocalSymbolTable localSymbolTable = this.localSymbolTable;
        if (localSymbolTable.hasSymbol(IdentName)) {
            return 1;
        }

        if (this.globalSymbolTable.hasSymbol(IdentName)) {
            return 2;
        }
        return 0;
    }

    public boolean judgeConst(int level, String tokenName) throws AnalyzeError {
        if (this.localSymbolTable.getSymbolEntry(tokenName) != null) {

            return this.localSymbolTable.getSymbolEntry(tokenName).isConstant();

        } else if (this.globalSymbolTable.getSymbolEntry(tokenName) != null) {
            return this.globalSymbolTable.getSymbolEntry(tokenName).isConstant();
        } else {
            throw new AnalyzeError(NotDeclared);
        }

    }

    /**
     * 在函数表中寻找函数是否已经被声明
     * 声明返回 true 否则返回 false
     * @param functionName
     * @return
     */
    public boolean searchForFunction(String functionName) {
        return this.functionTable.hasFunction(functionName);
    }

    public String getFunctionReturnType(String functionName) {
        return this.functionTable.getReturnTypeOfFunction(functionName);
    }

    /**
     * set函数 设置本类的全局符号表
     * @param globalSymbolTable
     */
    public void setGlobalSymbolTable(GlobalSymbolTable globalSymbolTable) {
        this.globalSymbolTable = globalSymbolTable;
    }

    public int getNumOfFunctionParam(String functionName) {
        return this.functionTable.getNumOfFunction(functionName);
    }

    public void addFunctionDefToOzero(FunctionDef functionDef) {
        this.oZero.addFunctionDef(functionDef);
    }

    public int getNameOfFunctionDef (FunctionDef functionDef) {
        return this.oZero.getNameOfFunctionDef(functionDef);
    }

    public ArrayList<Integer> generateGlobalDefFunctionName(String functionName) {
        ArrayList<Integer> retList = new ArrayList<>();
        for (int i = 0; i < functionName.length(); i ++) {
            retList.add((int) functionName.charAt(i));
        }
        return retList;
    }

    public void addGlobalDefToOzero(GlobalDef globalDef) {
        this.oZero.addGlobalDef(globalDef);
    }

    public void addGlobalToStack(String functionName) {
        int offset = this.globalStack.getGlobalStackPointer();
        this.globalStack.addToGlobalStack(functionName, offset);
        this.globalStack.addGlobalStackPointer();
    }

    public void addLocalVariableToStack(String localVariableName) {
        int offset = this.localVariableStack.getLocalStackPointer();
        this.localVariableStack.addToLocalStack(localVariableName, offset);
        this.localVariableStack.addLocalStackPointer();
    }

    public void addParamToStack(String paramName) {
        int offset = this.paramAndReturnValueStack.getParamReturnStackPointer();
        this.paramAndReturnValueStack.addToParamReturnStack(paramName, offset);
        this.paramAndReturnValueStack.addParamReturnStackPointer();
    }

    public void clearLocalSymbolTable() {
        this.localSymbolTable.resetLocalSymbolTable();
    }

    public void clearInstructions() {
        this.instructions.clear();
    }

    public void clearLocalVariableStack() {
        this.localVariableStack.resetLocalVariableStack();
    }

    public void clearParamAndReturnValueStack() {
        this.paramAndReturnValueStack.resetParamAndReturnValueStack();
    }

    public void addReturnInstruction() {
        NavmInstruction navmInstruction1 = new NavmInstruction();
        navmInstruction1.setHasParam(false);
        navmInstruction1.setOpcode(navmInstruction1.getOpcode(Instructions.ret));
        this.instructions.add(navmInstruction1);
    }

    public void addLocaInstruction(Token token) {
        NavmInstruction navmInstruction1 = new NavmInstruction();
        int offset = this.localVariableStack.getOffset(String.valueOf(token.getValue()));
        navmInstruction1.setOpcode(navmInstruction1.getOpcode(Instructions.loca));
        navmInstruction1.setParam(offset);
        navmInstruction1.setHasParam(true);
        this.instructions.add(navmInstruction1);
    }

    public void addGlobaInstruction(Token token) {
        NavmInstruction navmInstruction1 = new NavmInstruction();
        int offset = this.globalStack.getGlobalStackOffset(String.valueOf(token.getValue()));
        navmInstruction1.setOpcode(navmInstruction1.getOpcode(Instructions.globa));
        navmInstruction1.setParam(offset);
        navmInstruction1.setHasParam(true);
        this.instructions.add(navmInstruction1);
    }

    public void addArgaInstruction(Token token) {
        NavmInstruction navmInstruction = new NavmInstruction();
        int offset = this.paramAndReturnValueStack.getOffset(String.valueOf(token.getValue()));
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.arga));
        navmInstruction.setHasParam(true);
        navmInstruction.setParam(offset - argaFlag);
        this.instructions.add(navmInstruction);
    }

    public void addStoreInstruction() {
        NavmInstruction navmInstruction2 = new NavmInstruction();
        navmInstruction2.setOpcode(navmInstruction2.getOpcode(Instructions.store));
        navmInstruction2.setHasParam(false);
        this.instructions.add(navmInstruction2);
    }

    public void addLoadInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.load));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addPushInstruction(int param) {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.push));
        navmInstruction.setParam(param);
        navmInstruction.setHasParam(true);
        this.instructions.add(navmInstruction);
    }

    public void addStackAllocInstruction(int param) {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.stackalloc));
        navmInstruction.setParam(param);
        navmInstruction.setHasParam(true);
        this.instructions.add(navmInstruction);
    }

    public void addCallInstruction(int param) {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.call));
        navmInstruction.setParam(param);
        navmInstruction.setHasParam(true);
        this.instructions.add(navmInstruction);
    }

    public void addAddInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.add));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addSubInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.sub));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addMulInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.mul));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addDivInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.div));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public ArrayList<Integer> generateGlobalVariableList() {
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 0; i < 8; i ++) {
            list.add((int)0);
        }
        return list;
    }

    public void printInstructions() {
        int num = 0;
        for (NavmInstruction navmInstruction : instructions) {
            System.out.println("    " + num + " : " + navmInstruction);
            num ++;
        }
    }

    public void printInstructions(FunctionDef functionDef) {
        int num = 0;
        for (NavmInstruction navmInstruction : functionDef.getBody()) {
            System.out.println("    " + num + " : " + navmInstruction);
            num ++;
        }
    }

    public void addArga0Instruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        int offset = 0;
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.arga));
        navmInstruction.setHasParam(true);
        navmInstruction.setParam(offset);
        this.instructions.add(navmInstruction);
    }

    public void addNegInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.neg));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addPopNInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.popn));
        navmInstruction.setHasParam(true);
        navmInstruction.setParam(1);
        this.instructions.add(navmInstruction);
    }

    public void addCmpiInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.cmpi));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addBrTrueInstruction(int param) {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.BrTrue));
        navmInstruction.setHasParam(true);
        navmInstruction.setParam(param);
        this.instructions.add(navmInstruction);
    }

    public void addNotInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.not));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addSetLtInstruction () {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.SetLt));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public void addSetGtInstruction() {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.SetGt));
        navmInstruction.setHasParam(false);
        this.instructions.add(navmInstruction);
    }

    public NavmInstruction addBrInstruction(int param) {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.Br));
        navmInstruction.setHasParam(true);
        navmInstruction.setParam(param);
        this.instructions.add(navmInstruction);
        return navmInstruction;
    }

    public void addCallnameInstruction(int param) {
        NavmInstruction navmInstruction = new NavmInstruction();
        navmInstruction.setOpcode(navmInstruction.getOpcode(Instructions.callname));
        navmInstruction.setHasParam(true);
        navmInstruction.setParam(param);
        this.instructions.add(navmInstruction);
    }

    public void setBrInstructionParam(NavmInstruction instruction, int param) {
        instruction.setParam(param);
    }

    public boolean isLibraryFunction(String functionName) {
        if (functionName.equals("putint") || functionName.equals("putstr") || functionName.equals("putln") || functionName.equals("getint")
        || functionName.equals("putchar"))
        {
            return true;
        }
        return false;
    }

    public boolean hasMainFunction() {
        return hasFunction("main");
    }

    public int getGlobalDefIndex () {
        return this.oZero.getGlobalDefIndex();
    }
}

