/*
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

/**
 * Unit tests for {@link CoalesceVariableNames}
 *
 */
public final class CoalesceVariableNamesTest extends CompilerTestCase {
  // The spacing in this file is not exactly standard but it greatly helps
  // picking out which variable names are merged.

  private boolean usePseudoName = false;

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    usePseudoName = false;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        // enableNormalize would require output of CoalesceVariableNames to be normalized,
        // so we just manually normalize the input instead.
        Normalize normalize = new Normalize(compiler, false);
        normalize.process(externs, root);
        new CoalesceVariableNames(compiler, usePseudoName).process(externs, root);
      }
    };
  }

  public void testSimple() {
    inFunction(
        "var x; var y; x=1; x; y=1; y; return y",
        "var x;        x=1; x; x=1; x; return x");

    inFunction(
        "var x,y; x=1; x; y=1; y",
        "var x  ; x=1; x; x=1; x");

    inFunction("var x; var y; x=1; y=2; y; x");

    inFunction(
        "y=0; var x, y; y; x=0; x",
        "y=0; var y   ; y; y=0;y");

    inFunction(
        "var x,y; x=1; y=x; y",
        "var x  ; x=1; x=x; x");

    inFunction(
        "var x,y; x=1; y=x+1; y",
        "var x  ; x=1; x=x+1; x");

    inFunction(
        "x=1; x; y=2; y; var x; var y",
        "x=1; x; x=2; x; var x");

    inFunction(
        "var x=1; var y=x+1; return y",
        "var x=1;     x=x+1; return x");

    inFunction("var x=1; var y=0; x = x + 1; y");

    inFunction("var x=1; x+=1;     var y=0; y", "var x=1; x = x + 1;     x=0; x");

    inFunction(
        "var x=1; foo(bar(x+=1));     var y=0; y", "var x=1; foo(bar(x = x + 1));    x=0; x");

    inFunction("var y; var x=1; f(x = x + 1, y)");

    inFunction("var x; var y; y = y + 1, y, x = 1; x");
  }

  public void testMergeThreeVarNames() {
    inFunction(
        "var x,y,z; x=1; x; y=1; y; z=1; z",
        "var x    ; x=1; x; x=1; x; x=1; x");
  }

  public void testDifferentBlock() {
    inFunction(
        "if(1) { var x = 0; x } else { var y = 0; y }",
        "if(1) { var x = 0; x } else {     x = 0; x }");
  }

  public void testLoops() {
    inFunction("var x; for ( ; 1; ) { x; x = 1; var y = 1; y }");

    inFunction(
        "var y = 1; y; for ( ; 1; ) { var x = 1; x }",
        "var y = 1; y; for ( ; 1; ) {     y = 1; y }");
  }

  public void testEscaped() {
    inFunction("function f() { x } var x = 1; x; var y = 0; y; f()");
  }

  public void testFor() {
    inFunction(
        "var x = 1; x; for (;;) var y; y = 1; y",
        "var x = 1; x; for (;;)      ; x = 1; x");
  }

  public void testForIn() {
    // We lose some precision here, unless we have "branched-backward-dataflow".
    inFunction("var x = 1; var k; x; var y; for ( y in k ) y");

    inFunction(
        "var x = 1,     k; x; y = 1; for (var y in k) { y }",
        "var x = 1; var k; x; x = 1; for (    x in k) { x }");
  }

  public void testForOf() {
    // We lose some precision here, unless we have "branched-backward-dataflow".
    inFunction("var x = 1; var k; x; var y; for ( y of k ) y");

    inFunction(
        "var x = 1,     k; x; y = 1; for (var y of k) { y }",
        "var x = 1; var k; x; x = 1; for (    x of k) { x }");
  }

  public void testLoopInductionVar() {
    inFunction(
        "for(var x = 0; x < 10; x++){}"
            + "for(var y = 0; y < 10; y++){}"
            + "for(var z = 0; z < 10; z++){}",
        "var x=0;" + "for(;x<10;x++);" + "x=0;" + "for(;x<10;x++);" + "x=0;" + "for(;x<10;x++) {}");

    inFunction(
        "for(var x = 0; x < 10; x++){z}" + "for(var y = 0, z = 0; y < 10; y++){z}",
        "var x=0;for(;x<10;x++)z;x=0;var z=0;for(;x<10;x++)z");

    inFunction("var x = 1; x; for (var y; y=1; ) {y}", "var x = 1; x; for ( ; x=1; ) {x}");

    inFunction("var x = 1; x; y = 1; while(y) var y; y", "var x = 1; x; x = 1; for (;x;); x");

    // It's not the job of the coalesce variables pass to remove unused labels
    inFunction("var x = 1; x; f:var y; y=1", "var x = 1; x; f:{} x=1");
  }

  public void testSwitchCase() {
    inFunction(
        "var x = 1; switch(x) { case 1: var y; case 2: } y = 1; y",
        "var x = 1; switch(x) { case 1:        case 2: } x = 1; x");
  }

  public void testDuplicatedVar() {
    // Is there a shorter version without multiple declarations?
    inFunction(
        "z = 1; var x = 0; x; z; var y = 2,     z = 1; y; z;",
        "z = 1; var x = 0; x; z;     x = 2; var z = 1; x; z;");
  }

  public void testTryCatch() {
    inFunction("try {} catch (e) { } var x = 4; x;");
    inFunction("var x = 4; x; try {} catch (e) { }");
  }

  public void testDeadAssignment() {
    inFunction("var x = 6; var y; y = 4 ; x");
    inFunction("var y = 3; y = y + 4; x");
    inFunction("var y = 3; y = y + 1; x");
    inFunction("y = 3; var x; var y = 1 ; x");
  }

  public void testParameter() {
    test("function FUNC(param) {var x = 0; x}",
         "function FUNC(param) {param = 0; param}");
  }

  public void testParameter2() {
    // Make sure two formal parameter name never merges.
    testSame("function FUNC(x,y) {x = 0; x; y = 0; y}");
    testSame("function FUNC(x,y,z) {x = 0; x; y = 0; z = 0; z}");
  }

  public void testParameter3() {
    // Make sure the formal parameter declaration is consider a def.
    testSame("function FUNC(x) {var y; y = 0; x; y}");
  }

  public void testParameter4a() {
    // Make sure that we do not merge two-arg functions because of the
    // IE sort bug (see comments in computeEscaped)
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    test(
        "function FUNC(x, y) {var a,b; y; a=0; a; x; b=0; b}",
        "function FUNC(x, y) {var a;   y; a=0; a; x; a=0; a}");
  }

  public void testParameter4b() {
    // Go ahead and merge, if language-out is ES5, because that means IE8 does
    // not need to be supported.
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "function FUNC(x, y) {var a,b; y; a=0; a; x; b=0; b}",
        "function FUNC(x, y) {         y; y=0; y; x; x=0; x}");
  }

  public void testParameter5() {
    // Merge parameters
    test(
        "function FUNC(x, y, z) {var a,b; y; a=0; a; x; b=0; b}",
        "function FUNC(x, y, z) {         y; y=0; y; x; x=0; x}");
  }

  public void testLiveRangeChangeWithinCfgNode() {
    inFunction("var x; var y; x = 1, y = 2, y, x");
    inFunction("var x; var y; x = 1,x; y");

    // We lose some precisions within the node itself.
    inFunction("var x; var y; y = 1, y, x = 1; x");

    inFunction("var x; var y; y = 1; y, x = 1; x", "var x; x = 1; x, x = 1; x");

    inFunction("var x; var y; y = 1, x = 1, x, y = y + 1, y");

    inFunction("var x; var y; y = 1, x = 1, x, y = y + 1, y");
  }

  public void testLiveRangeChangeWithinCfgNode2() {
    inFunction("var x; var y; var a; var b; y = 1, a = 1, y, a, x = 1, b = 1; x; b");

    inFunction(
        "var x; var y; var a; var b; y = 1, a = 1, y, a, x = 1; x; b = 1; b",
        "var x; var y; var a;        y = 1, a = 1, y, a, x = 1; x; x = 1; x");

    inFunction(
        "var x; var y; var a; var b; y = 1, a = 1, y, x = 1; a; x; b = 1; b",
        "var x; var y; var a;        y = 1, a = 1, y, x = 1; a; x; x = 1; x");
  }

  public void testFunctionNameReuse() {
    inFunction("function x() {}; x(); var y = 1; y");

    inFunction("function x() { } x(); var y = 1; y");

    inFunction("function x() { } x(); var y = 1; y");

    // Can't merge because of possible escape.
    inFunction("function x() {return x}; x(); var y = 1; y");

    inFunction("function x() {} var y = 1; y; x");

    inFunction("function x() { } var y = 1; y; x");

    inFunction("function x() { } var y = 1; y; x = 1; x");

    inFunction("function x() {} var y = 1; y; x = 1; x");
  }

  public void testBug1401831() {
    // Verify that we don't wrongly merge "opt_a2" and "i" without considering
    // arguments[0] aliasing it.
    String src =
        LINE_JOINER.join(
            "function f(opt_a2){",
            "  var buffer;",
            "  if(opt_a2){",
            "    var i=0;",
            "    for(;i<arguments.length;i++)buffer=buffer+arguments[i]",
            "  }",
            "return buffer",
            "}");
    testSame(src);
  }

  public void testDeterministic() {
    // Make the variable interference graph a pentagon.
    //         a - b
    //        /     \
    //        e     c
    //         \   /
    //           d
    // The coloring partitioning would be:
    //  a = { a, c }
    //  b = { b, d }
    //  e = { e }
    inFunction(
        "  var a;"
            + "  var b;"
            + "  var c;"
            + "  var d;"
            + "  var e;"
            + "  a=1; b=1; a; b;"
            + "  b=1; c=1; b; c;"
            + "  c=1; d=1; c; d;"
            + "  d=1; e=1; d; e;"
            + "  e=1; a=1; e; a;",
        "  var a;"
            + "  var b;"
            + "  var e;"
            + "  a=1; b=1; a; b;"
            + "  b=1; a=1; b; a;"
            + "  a=1; b=1; a; b;"
            + "  b=1; e=1; b; e;"
            + "  e=1; a=1; e; a;");

    // If we favor "d" first by declaring "d" earlier,
    // the coloring partitioning would be:
    //  b = { b, e }
    //  d = { d, a }
    //  c = { c }
    inFunction(
        "var d,a,b,c,e;"
            + "  a=1; b=1; a; b;"
            + "  b=1; c=1; b; c;"
            + "  c=1; d=1; c; d;"
            + "  d=1; e=1; d; e;"
            + "  e=1; a=1; e; a;",
        "  var d;var b;var c;"
            + "  d=1;b=1;d;b;"
            + "  b=1;c=1;b;c;"
            + "  c=1;d=1;c;d;"
            + "  d=1;b=1;d;b;"
            + "  b=1;d=1;b;d");
  }

  // Sometimes live range can be cross even within a VAR declaration.
  public void testVarLiveRangeCross() {
    inFunction("var a={}; var b=a.S(); b", "var a={};     a=a.S(); a");

    inFunction(
        "var a = {}; var b = a.S(),     c = b.SS(); b; c",
        "var a = {};     a = a.S(); var c = a.SS(); a; c");

    inFunction(
        "var a={}; var b=a.S(); var c=a.SS(); var d=a.SSS(); b; c; d",
        "var a={}; var b=a.S(); var c=a.SS();     a=a.SSS(); b; c; a");

    inFunction(
        "var a={}; var b=a.S(); var c=a.SS(); var d=a.SSS(); b; c; d",
        "var a={}; var b=a.S(); var c=a.SS();     a=a.SSS(); b; c; a");

    inFunction("var a={}; d=1; d; var b=a.S(); var c=a.SS(); var d=a.SSS(); b; c; d");
  }

  public void testBug1445366() {
    // An assignment might not be complete if the RHS throws an exception.
    inFunction(
        " var iframe = getFrame();" +
        " try {" +
        "   var win = iframe.contentWindow;" +
        " } catch (e) {" +
        " } finally {" +
        "   if (win)" +
        "     this.setupWinUtil_();" +
        "   else" +
        "     this.load();" +
        " }");

    // Verify that we can still coalesce it if there are no handlers.
    inFunction(
        " var iframe = getFrame();" +
        " var win = iframe.contentWindow;" +
        " if (win)" +
        "   this.setupWinUtil_();" +
        " else" +
        "   this.load();",

        " var iframe = getFrame();" +
        " iframe = iframe.contentWindow;" +
        " if (iframe)" +
        "   this.setupWinUtil_();" +
        " else" +
        "   this.load();");
  }

  public void testCannotReuseAnyParamsBug() {
    testSame("function handleKeyboardShortcut(e, key, isModifierPressed) {\n" +
        "  if (!isModifierPressed) {\n" +
        "    return false;\n" +
        "  }\n" +
        "  var command;\n" +
        "  switch (key) {\n" +
        "    case 'b': // Ctrl+B\n" +
        "      command = COMMAND.BOLD;\n" +
        "      break;\n" +
        "    case 'i': // Ctrl+I\n" +
        "      command = COMMAND.ITALIC;\n" +
        "      break;\n" +
        "    case 'u': // Ctrl+U\n" +
        "      command = COMMAND.UNDERLINE;\n" +
        "      break;\n" +
        "    case 's': // Ctrl+S\n" +
        "      return true;\n" +
        "  }\n" +
        "\n" +
        "  if (command) {\n" +
        "    this.fieldObject.execCommand(command);\n" +
        "    return true;\n" +
        "  }\n" +
        "\n" +
        "  return false;\n" +
        "};");
  }

  public void testForInWithAssignment() {
    inFunction(
        "function f(commands) {"
            + "  var k, v, ref;"
            + "  for (k in ref = commands) {"
            + "    v = ref[k];"
            + "    alert(k + ':' + v);"
            + "  }"
            + "}",
        "function f(commands){"
            + "var k;"
            + "var ref;"
            + "for(k in ref = commands) {"
            + "  commands=ref[k];"
            + "  alert(k+':'+commands)"
            + "}}");
  }

  public void testUsePseudoNames() {
    usePseudoName = true;
    inFunction("var x   = 0; print(x  ); var   y = 1; print(  y)",
               "var x_y = 0; print(x_y);     x_y = 1; print(x_y)");

    inFunction("var x_y = 1; var x   = 0; print(x  ); var     y = 1;" +
               "print(  y); print(x_y);",

               "var x_y = 1; var x_y$ = 0; print(x_y$);     x_y$ = 1;" + "" +
               "print(x_y$); print(x_y);");

    inFunction(
        LINE_JOINER.join(
            "var x_y = 1; ",
            "function f() {",
            "  var x    = 0;",
            "  print(x  );",
            "  var y = 1;",
            "  print( y);",
            "  print(x_y);",
            "}"),
        LINE_JOINER.join(
            "function f(){",
            "var x_y$=0;",
            "print(x_y$);",
            "x_y$=1;",
            "print(x_y$);",
            "print(x_y)",
            "}",
            "var x_y=1"));

    inFunction(
        LINE_JOINER.join(
            "var x   = 0;",
            "print(x  );",
            "var   y = 1;",
            "print(  y); ",
            "var closure_var;",
            "function bar() {",
            "  print(closure_var);",
            "}"),
        LINE_JOINER.join(
            "function bar(){",
            "  print(closure_var)",
            "}",
            "var x_y=0;",
            "print(x_y);",
            "x_y=1;",
            "print(x_y);",
            "var closure_var"));
  }

  public void testUsePseudoNamesWithLets() {
    usePseudoName = true;
    inFunction(
        LINE_JOINER.join(
            "var x_y = 1; ",
            "function f() {",
            "  let x    = 0;",
            "  print(x  );",
            "  let y = 1;",
            "  print( y);",
            "  print(x_y);",
            "}"),
        LINE_JOINER.join(
            "function f(){",
            "  var x_y$=0;",
            "  print(x_y$);",
            "  x_y$ = 1;",
            "  print(x_y$);",
            "  print(x_y)",
            "}",
            "var x_y=1"));
  }

  public void testMaxVars() {
    String code = "";
    for (int i = 0; i < LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE + 1; i++) {
      code += String.format("var x%d = 0; print(x%d);", i, i);
    }
    inFunction(code);
  }

  // Testing Es6 features
  public void testCoalesceInInnerBlock() {
    inFunction(
        "{ var x = 1; var y = 2; y }",
        "{ var x = 1;     x = 2; x }");

    inFunction(
        "var x = 1; var y = 2; y;",
        "var x = 1;     x = 2; x;");
  }

  public void testLetSimple() {
    inFunction(
        "let x = 0; x; let y = 5; y",
        "var x = 0; x;     x = 5; x");

    inFunction(
        "var x = 1; var y = 2; { let z = 3; y; }",
        "var x = 1;     x = 2; { let z = 3; x; }");

    // First let in a block - It is unsafe for { let x = 0; x; } let y = 1; to be coalesced as
    // { let x = 0; x; } x = 1; because x will be out of scope outside of the inner scope!
    inFunction(
        "{ let x = 0; x; } let y = 5; y;",
        "{ var x = 0; x; }     x = 5; x;");

    // The following situation will never happen in practice because at this point, the code has
    // been normalized so no two variables will have the same name
    // --> var x = 1; var y = 2; { let x = 3; y }
  }

  public void testLetDifferentBlocks() {
    inFunction(
        "var x = 0; if (1) { let y = 1; x } else { let z = 1; x }",
        "var x = 0; if (1) { var y = 1; x } else {     y = 1; x }");

    inFunction(
        "var x = 0; if (1) { let y = 1; y } else { let z = 1 + x; z }",
        "var x = 0; if (1) {     x = 1; x } else {     x = 1 + x; x }");

    inFunction(
        "var x = 0; if (1) { let y = 1; y } else { let z = 1; z }; x",
        "var x = 0; if (1) { var y = 1; y } else {     y = 1; y }; x");

    inFunction(
        "if (1) { var x = 0; let y = 1; y + x} else { let z = 1; z } y;",
        "if (1) { var x = 0; let y = 1; y + x} else {     x = 1; x } y;");

    inFunction(
        "if(1) { var x = 0; x } else { var y = 0; y }",
        "if(1) { var x = 0; x } else {     x = 0; x }");

    inFunction(
        LINE_JOINER.join(
            "if (a) {",
            "   return a;",
            " } else {",
            "   let b = a;",
            "   let c = 1;",
            "   return c;",
            " }",
            " return a;"),
        LINE_JOINER.join(
            "if (a) {",
            "    return a;",
            "  } else {",
            "    var b = a;",
            "        b = 1;",
            "    return b;",
            "  }",
            "  return a;"));
  }

  public void testLetWhileLoops() {
    // Simple
    // It violates the temporal deadzone for let x = 1; while (1) { x = 2; x; let x = 0; x } to
    // be output.
    inFunction("let x = 1; for(;1;) { x; x = 2; let y = 0; y }");

    inFunction("let x = 1; for(;1;) { x = 2; x; let y = 0; y } x;");
  }

  public void testLetForLoops() {
    // TODO (simranarora) We should get rid of declaration hoisting from the normalize pass.
    // Right now, because of declaration hoisting, this following test reads the expected code as:
    // var x = 1; for ( ; x < 10; x++) { let y = 2; x + y } x = 3
    inFunction(
        "for (let x = 1; x < 10; x ++) { let y = 2; x + y; } let z = 3;",
        "for(var x=1;x<10;x++){let y=2;x+y}x=3");

    inFunction(
        "var w = 0; for (let x = 1; x < 10; x ++) { let y = 2; x + y; } var z = 3;",
        "var w = 0; for (    w = 1; w < 10; w ++) { let y = 2; w + y; }     w = 3;");

    // Closure capture of the let variable
    // Here z should not be coalesced because variables used in functions are considered escaped
    // and this pass does not touch any escaped variables
    inFunction("let x = 3; for (let z = 1; z < 10; z++) { use(() => {z}); }");

    inFunction("for (let x = 1; x < 10; x++) { use(() => {x}); } let z = 3;");

    // Multiple lets declared in loop head
    inFunction("for (let x = 1, y = 2, z = 3; (x + z) < 10; x ++) { x + z; }");

    // Here the variable y is redeclared because the variable z in the header of the for-loop has
    // not been declared before
    inFunction(
        "let y = 2; for (let x = 1, z = 3; (x + z) < 10; x ++) { x + z; }",
        "var y = 2; for (var y = 1, z = 3; (y + z) < 10; y ++) { y + z; }");
  }

  public void testArrowFunctions() {
    inFunction(
        "var x = 1; var y = () => { let z = 0; z }",
        "var x = 1;     x = () => { let z = 0; z }");

    inFunction(
        "var x = 1; var y = () => { let z = 0; z }; y();",
        "var x = 1;     x = () => { let z = 0; z }; x();");

    inFunction(
        "var x = 1; var y = () => { let z = 0; z }; x;",
        "var x = 1; var y = () => { let z = 0; z }; x;");

    inFunction(
        "var x = () => { let z = 0; let y = 1; y }",
        "var x = () => { var z = 0;     z = 1; z }");

    inFunction(
        "var x = 1; var y = 2; var f = () => x + 1",
        "var x = 1; var y = 2;     y = () => { return x + 1; }");

    // Coalesce with arrow function parameters
    inFunction(
        "(x) => { var y = 1; y; }",
        "(x) => {     x = 1; x; }");

    inFunction(
        "(x) => { let y = 1; y; }",
        "(x) => {     x = 1; x; }");
  }

  public void testCodeWithTwoFunctions() {
    // We only want to coalesce within a function, not across functions
    test(
        LINE_JOINER.join(
            "function FUNC1() {",
            "  var x = 1; ",
            "  var y = 2; ",
            "          y; ",
            "}",
            "function FUNC2() {",
            "  var z = 3; ",
            "  var w = 4; ",
            "          w; ",
            "}"),
        LINE_JOINER.join(
            "function FUNC1() {",
            "  var x = 1; ",
            "      x = 2; ",
            "          x; ",
            "}",
            "function FUNC2() {",
            "  var z = 3; ",
            "      z = 4; ",
            "          z; ",
            "}"));

    // Two arrow functions
    test(
        LINE_JOINER.join(
            "() => { var x = 1; var y = 2; y; };",
            "() => { var z = 3; var w = 4; w; };"),
        LINE_JOINER.join(
            "() => { var x = 1;     x = 2; x; };",
            "() => { var z = 3;     z = 4; z; };"));
  }

  public void testNestedFunctionCoalescing() {
    test(
        LINE_JOINER.join(
            "function FUNC1() {",
            "  var x = 1; ",
            "  var y = 2; ",
            "          y; ",
            "  function FUNC2() {",
            "    var z = 3; ",
            "    var w = 4; ",
            "            w; ",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function FUNC1() {",
            "  function FUNC2() {",
            "    var z = 3;",
            "        z = 4;",
            "            z",
            "  }",
            "  var x = 1;",
            "  x = 2;",
            "  x",
            "}"));
  }

  private void inFunction(String src) {
    testSame("function FUNC(){" + src + "}");
  }

  private void inFunction(String src, String expected) {
    test("function FUNC(){" + src + "}",
         "function FUNC(){" + expected + "}");
  }
}
