import styles from "./InfoBanner.module.css";

export function InfoBanner({ message }: { message: string }) {
  return (
    <div className={styles.banner} role="status">
      {message}
    </div>
  );
}