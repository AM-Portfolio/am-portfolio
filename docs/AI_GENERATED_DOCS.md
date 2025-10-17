# Overview of the Codebase
The AM-Portfolio codebase is a personal portfolio website designed to showcase projects, skills, and experiences. The codebase is built using a combination of HTML, CSS, and JavaScript, with a focus on creating a responsive and user-friendly interface.

## Key Components and Their Purposes
The codebase consists of the following key components:

* **index.html**: The main entry point of the website, responsible for rendering the portfolio's homepage.
* **styles.css**: A stylesheet that defines the visual styling and layout of the website.
* **script.js**: A JavaScript file that handles interactive elements and dynamic content on the website.
* **projects**: A directory containing individual project pages, each with its own HTML, CSS, and JavaScript files.

## API Documentation
The codebase does not have a public API, as it is a personal portfolio website. However, the following APIs are used internally:

* **GitHub API**: Used to fetch project data and repositories.
* **EmailJS API**: Used to handle contact form submissions.

### GitHub API
The GitHub API is used to fetch project data and repositories. The API endpoint used is `https://api.github.com/users/{username}/repos`, where `{username}` is the GitHub username.

### EmailJS API
The EmailJS API is used to handle contact form submissions. The API endpoint used is `https://api.emailjs.com/api/v1.0/email/send`, with the following parameters:

* **service_id**: The ID of the EmailJS service.
* **template_id**: The ID of the email template.
* **user_id**: The ID of the EmailJS user.
* **template_params**: An object containing the email template parameters.

## Usage Examples
To use the codebase, follow these steps:

1. Clone the repository using `git clone https://github.com/AM-Portfolio/am-portfolio.git`.
2. Install the required dependencies using `npm install`.
3. Start the development server using `npm start`.
4. Open the website in a web browser using `http://localhost:3000`.

### Creating a New Project Page
To create a new project page, follow these steps:

1. Create a new directory in the `projects` directory.
2. Create a new HTML file in the directory, e.g. `index.html`.
3. Create a new CSS file in the directory, e.g. `styles.css`.
4. Create a new JavaScript file in the directory, e.g. `script.js`.
5. Update the `index.html` file to include the new project page.

### Updating the Portfolio
To update the portfolio, follow these steps:

1. Update the `index.html` file to include new project pages or remove old ones.
2. Update the `styles.css` file to reflect any changes to the visual styling.
3. Update the `script.js` file to reflect any changes to the interactive elements.

## Architecture Notes
The codebase uses a modular architecture, with each component responsible for a specific task. The `index.html` file serves as the main entry point, while the `styles.css` and `script.js` files handle the visual styling and interactive elements, respectively. The `projects` directory contains individual project pages, each with its own HTML, CSS, and JavaScript files.

The codebase uses the following design patterns:

* **Modular design**: Each component is responsible for a specific task, making it easier to maintain and update the codebase.
* **Separation of concerns**: The HTML, CSS, and JavaScript files are separated, making it easier to update and maintain the codebase.

### Directory Structure
The codebase has the following directory structure:
```markdown
am-portfolio/
|-- index.html
|-- styles.css
|-- script.js
|-- projects/
    |-- project1/
        |-- index.html
        |-- styles.css
        |-- script.js
    |-- project2/
        |-- index.html
        |-- styles.css
        |-- script.js
    |-- ...
|-- package.json
|-- README.md
```
### Code Syntax
The codebase uses the following syntax:

* **HTML**: The codebase uses HTML5 syntax, with a focus on semantic markup.
* **CSS**: The codebase uses CSS3 syntax, with a focus on modular and reusable styles.
* **JavaScript**: The codebase uses ES6 syntax, with a focus on modular and reusable code.

### Parameters
The codebase uses the following parameters:

* **GitHub username**: The GitHub username is used to fetch project data and repositories.
* **EmailJS service ID**: The EmailJS service ID is used to handle contact form submissions.
* **EmailJS template ID**: The EmailJS template ID is used to handle contact form submissions.
* **EmailJS user ID**: The EmailJS user ID is used to handle contact form submissions.

### Usage
To use the codebase, follow these steps:

1. Clone the repository using `git clone https://github.com/AM-Portfolio/am-portfolio.git`.
2. Install the required dependencies using `npm install`.
3. Start the development server using `npm start`.
4. Open the website in a web browser using `http://localhost:3000`.

Note: This documentation is based on the provided codebase and may not reflect the actual implementation.