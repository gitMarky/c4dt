# CHANGELOG

* 2.2.1:

	* Then I decided to change all the internal lists to plain arrays or something

* 2.2.0:

	* After a long period of inward-facing ponderance, release whatever the current code state is as a version
	* Halfheartedly support anonymous func() syntax required for new effects

* 2.1.0:

	* Non-GUI way to specify per-project-engine path
	* Outline sort order: Functions, Variables, no sorting of functions by visibility. Function nodes don't get automatically expanded

* 2.0.3:

	* ???

* 2.0.2:

	* Differentiate between regular locals and locals created by assigning a property - the latter ones always require qualification and thus do not hide definitions with the same name as the assigned local

* 2.0.1:

	* Replace hiding of types of problems with ProblemHandlingMap.txt (http://deenosaurier.de/c4dt/problemfiltering.html)
	* Support ParamaterDefs.txt files in OpenClonk

* 2.0:

	* Require Java 8 because author got into replace-all-the-loops-with-streams frenzy
	* It's probably more buggy than before

* 1.9:

	* OpenClonk: Adjustments to c4dt's list of engine declarations to keep false errors to a minimum (git revision b70b195e2453c00de182ec6c86f02da93c170749)
	* Type inference: Set return type of functions to void when they do not return anything
	* Preferences: Allow setting number of threads to use when doing various parallel computations (like performing a clean build or some such)
	* Questionable performance optimizations
	* Hover over identifier in script: Show type inferred for the respective declaration at that location
	* Script: Mark having a function reference with no brackets right to -> as error
	* Fix renaming wizard
	* Fix quick assist proposals
	* C4ScriptEditor: Toggle parameters shown action (can be invoked to hide the automatic parameter help)

* 1.8.9:

	* OpenClonk: Debugging C4Scripts works with OpenClonk starting at git revision b6a01b62aaca66bf33a1a3671cc59c658766b80f
	* Bugs fixed, new bugs introduced

* 1.7.8:

	* Dabble Dabble
	* Refactoring
	* No progress whatsoever when it comes to actual features
	* Inserted completions for functions do not have brackets!

* 1.7.7:

	* C4Script Search Page
	* Instant completions
	* Internal refactorings which make everything even more broken than it was before

* 1.7.6:

	* Preliminary Scenario properties dialog (right-click on some scenario, select properties and then the scenario section)
	* Renaming definition folders should work better (or at all)
	* Type Inference changed, not necessarily improved
	* Lookup Declaration: Engine functions also listed; when selecting them the documentation page is opened
	* Bugs added
	* Bugs removed
	* Regressions predestined 

* 1.7.5:

	* Performance improvements (or maybe not)
	* Shortcuts contributed by plugin put into shortcut context "Clonk Editing" to not conflict with Java shortcuts etc
	* Default shortcuts for various commands

* 1.7.4:

	* Optimize "Reporting problems" building phase a bit
	* Fix some Clonk Rage parsing issues
	* Clonk Folder View -Add multi-selection -Add Look for ID command
	* TidyUpCodeInBulk: Temporarily disable auto-build
	* C4Group export: Indicate in Eclipse UI that c4group process still running
	* C4ScriptParser fixes
	* Teams.txt: Add some missing entries to definition
	* Probably more bugs with some fixed 

* 1.7.3:

	* Option to analyze code which can be turned off, leading to c4dt doing less on clean build and other occasions
	* Fixes

* 1.7.2:

	* Improved syntax coloring options (bold, italic, live-update)
	* TODO detection in c4script comments
	* Hopefully better support for array expressions, including slices
	* this.<var> assignments shown in outline and link-click navigable
	* C4ScriptEditor: Double clicking inside blocks selects the block
	* ClonkBuilder: Hopefully some improvements so that refreshing after repository update doesn't produce as many false errors
	* Javadoc comments picked up (@param, @return)
	* Remove silly refactoring prompt after DefCore.txt ID changed
	* TODO/FIXME markers comments containing those words
	* Fix issues with C4DT not finding the cpp source files to read OC definitions from
	* Add some more logic to prevent scenario-local #appendtos from bleeding over to other regions of a project outside of that scenario

* 1.7.1:

	* Improved function reference support
	* Support new {...} expression
	* Show included/appended declarations in outline
	* Silliness

* 1.7.0:

	* OC: Read documentation/definitions from repository (optional) -This is a combination of reading *.xml files from docs and extracting declarations from C4Script.cpp/C4ObjectScript.cpp/C4GameScript.cpp via pattern matching
	* Bugs fixed/New bugs introduced
	* Outline view has filter box
	* Multicore-Clean-Build (?)
	* Link-clicking sound names in Sound(...) calls

* 1.6.0:

	* More aggressive type inference
	* Bugs fixed/New bugs introduced

* 1.5.9:

	* Per-project setting to disable script parser warnings/error
	* Bugfixes and introduction of new bugs
	* Packed c4groups inside projects are treated as regular folders except that they are read-only (but explodable)
	* OC: Templates for new objects/scenarios improved
	* OC: Improved proplist support (links to effect vars in respective effect functions)
	* Customizable templates by creating a "C4DT Customization" Project (accessible through Clonk menu) -Copies of built-in template files can be created using the "C4DT Customization" submenu in the Project Explorer popup menu
	* Reorganize/rename some classes + add documentation

* 1.5.8:

	* Bugfixes
	* Probably more new bugs introduced
	* Quick fix support (invoked via Ctrl+1)
	* IniTextEditor supports collapsing and expanding sections (amazing!)
	* Improved OpenClonk compatibility
	* If Documentation browser  tab is left open, no new ones will be created when link-clicking other engine functions

* 1.5.7:

	* Tweak type inference system
	* Support for initializing non-const variables in their declaration
	* Improved OpenClonk compatibility
	* Only automatically insert closing brackets when writing a function call or inserting a completion (write 'Log(', get 'Log()')
	* Support links to packets in ini files (Scenario.txt: Definition1=Objects.c4d <- Objects.c4d can be ctrl-clicked)
	* Refine completion proposal showing
	* Refined script parser error messages
	* When editing inside functions in scripts, parser errors will be shown in near-realtime
	* Line endings in Clonk files can be converted using File -> Convert Line Delimiters To
	* Bugfixes

* 1.5.6:

	* Allow long-id constants (see Trajectory in OpenClonk)
	* Preserve empty lines when tidying up code
	* Fixes
	* Show errors and warnings immediately without needing to save a script
	* Return types of functions get shown in outline view

* 1.5.5:

	* Change type system so that variables can have multiple potential types leading to everything being more complicated and probably bug-prone
	* When determining that some variable will refer to an object of a certain type, that type will be shown in info hovers

* 1.5.4:

	* Profit!

* 1.5.3:

	* Support linking of C4Group files (use the Clonk Folder View for this)
	* Set engine for each project so one can share OC and CR projects in the same workspace
	* Fixes
	* Probably more bugs

* 1.5.2.4:

	* Fixes
	* Fix Landscape.txt parser so it accepts ranges and points
	* Refresh virtual nodes in project explorer
	* Make plugin compatible to Saros
	* Multiple editors for the same file update instantly
	* Some encoding issues fixed

* 1.5.2.3:

	* Stuff

* 1.5.2.2:

	* Fixes
	* Add Syntax Coloring preference page

* 1.5.2.1:

	* Fixes
	* Auto brackets ({}, ())
	* Preview view similar to the Editor.exe preview
	* ClonkBuilder speed improved in some cases

* 1.5.2:

	* Fixes
	* Context information (parameters) shown immediately

* 1.5.1.9:

	* Fix issue with parsing numbers in Landscape.txt files that were written with a '+' sign
	* Filter out some bogus proposals in the Declaration Chooser
	* Operators || and && don't return bool anymore but the type of their arguments
	* Custom arguments for launching scenarios
	* More specific warning for missing stringtbl entries
	* Reduce 'Too Many Parameters' false positives 

* 1.5.1.8:

	* Improvements in code formatting
	* Save modified files before performing rename refactoring
	* Some internal options for the code formatter that can be set by invoking the command SetCodeConversionOption("<option>", "<value>")
	* Respect tab settings in c4dt text editors
	* Support for referencing other Clonk projects whose indexed contents are then taken into account in various places

* 1.5.1.7:

	* One can now select folders as external libraries
	* No error when opening the revision of a script from svn
	* Advance state of german localization
	* Bug fixes
	* Definitions and global functions/variables in other Clonk projects can be found by referencing them (Project Properties->Project References)

* 1.5.1.6:

	* Bug fixes most probably

* 1.5.1.5:

	* Attempt at fixing a stupid problem

* 1.5.1.4:

	* If there are still indexdata files load them and automatically migrate to new storage location in .metadata
	* Detect some more errors

* 1.5.1.3:

	* Hopefully better importing of engine functions/constants from source

* 1.5.1.2:

	* Fixes

* 1.5.1.1:

	* Fixes
	* Comment at end of one-line function supported
	* Also warn about missing stringtbl entries if there is not even a stringtbl file

* 1.5.1:

	* Warn about possible infinite loops
	* Open DefCore.txt if no Script.c available
	* Warn about unreachable code in loop bodies
	* Warn about conditions that are always false or true
	* Open documentation in internal browser if one is available
	* Store index files inside the .metadata folder so it doesn't clutter up the project folder

* 1.5.0.9:

	* Descriptions for definitions now contain the path
	* No error when encountering #0 characters at the end of Scenario.txt and similar files
	* ObjectCall function string link-clickable
	* Fixes

* 1.5.0.8:

	* Fixes
	* UTF-8 encoded script handling
	* Attempts to retain type information for variables so that content assist is more helpful

* 1.5.0.7:

	* Fix

* 1.5.0.6:

	* Preferred language field editor is now combo 
	* Don't warn about missing string tbl entries in #appendto scripts
	* Put New Wizards into Clonk category 
	* Add some missing entries for various configuration files
	* Fix some wrong parameters of engine functions
	* Return as function warning is now an error, but only in #strict 2 mode
	* Implicit variable declaration in for (e in a) {...} loops

* 1.5.0.5:

	* Warn when referencing unused StringTbl entries
	* Preference for the documentation URL template
	* Encoding preference for external scripts

* 1.5.0.4:

	* Add Quick Import Action
	* Propose constants in ini files for entries like "Category"
	* Open documentation in browser when trying to get to the definition of an engine defined declaration
	* Converting old code in bulk (from the project explorer context menu) now shows a progress bar

* 1.5.0.3:

	* Make Quick Export work again on Windows/Linux
