Here is a list of rules to follow when giving code :
Please respect these rules as much as possible.

- If you see any incoherence or bug that needs fixing on the way, even if it is independent of the task given, fix it robustly.
- Take into account the modularity. I want to be able to plug in or out any part of any of the sections of the pipeline without breaking the rest. Use parameters as much as possible. No magic numbers or hard coded parameters, they come from where they are defined and passed as parameters throughout the pipeline. Some functions will have self defined parameters that do not need adjustments in the short term, but if it is bound to be changed, it needs to be defined in a master config file and passed as parameter throughout the pipeline to the user function.
- Make your code human readable, dont save 3 characters on a variable name if it renders it unintelligible (instead of atkrtg, use attack_rating, instead of hfclbk, use hf_cleaned_bookmakers).
- Make pretty code. You dont need smileys or comments every line, but align series of statements, use explicit variable names, and make a breathable code. Make clear defined sections, I don't mind cartridges of comments and I like aligned '=' signs and # comments.
- Whenever you implement a new features or make significant modifications to the code, please modify the README.md in consequence.
- If you find inconsistencies in the style of the code or the guidelines / best practices, please modify the code accordingly and then modify the AGENTS.md file to make sure the new for formalism is respected in the future. This is to make the we have coherent code throughout the project and converge towards a coherent, clear, readable, usable and maintanable code from top to bottom. (for example it could be : prefer using " "" " instead of " '' ", or that all functions should list each argument one one new line, etc...)
- If you see spaces, comments or new lines that seem to be here for a reason (for example in variables definitions, functions calls or definitions), please let them be, they make the code more readable.

Additionnal good practices and code formalism : 
- 

