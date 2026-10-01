import { Link } from 'react-router-dom';
export default function NotFound() {
  return (
    <div className="success">
      <h1>Page not found</h1>
      <p>The address may be mistyped or the page has moved.</p>
      <Link className="btn btn--primary" to="/">Go to home</Link>
    </div>
  );
}
