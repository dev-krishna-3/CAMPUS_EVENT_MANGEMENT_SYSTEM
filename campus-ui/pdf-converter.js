const { mdToPdf } = require('md-to-pdf');
const fs = require('fs');

(async () => {
  try {
    console.log("Starting conversion for report...");
    await mdToPdf({ path: 'C:\\Users\\krish\\.gemini\\antigravity\\brain\\44ef101b-7af6-4259-828b-a209f9e940fe\\project_comprehensive_report.md' }, { dest: 'C:\\Users\\krish\\OneDrive\\Desktop\\JAVA_PROGRAME\\project_comprehensive_report.pdf' });
    console.log("Report converted successfully.");
    
    console.log("Starting conversion for viva guide...");
    await mdToPdf({ path: 'C:\\Users\\krish\\.gemini\\antigravity\\brain\\44ef101b-7af6-4259-828b-a209f9e940fe\\viva_preparation_guide.md' }, { dest: 'C:\\Users\\krish\\OneDrive\\Desktop\\JAVA_PROGRAME\\viva_preparation_guide.pdf' });
    console.log("Viva guide converted successfully.");
  } catch (err) {
    console.error("Error during conversion:", err);
  }
})();
